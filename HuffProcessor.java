import java.util.zip.InflaterInputStream;
import java.util.*;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor  {
	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);

		out.writeBits(BITS_PER_INT , HUFF_TREE);
		writeHeader(root, out);

		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	private int[] readForCounts(BitInputStream in) {
		int[] frequencies = new int[ALPH_SIZE+1];
		while (true) {
			int bitRead = in.readBits(BITS_PER_WORD);
			if(bitRead == -1)
				break;
			frequencies[bitRead]++;
			frequencies[PSEUDO_EOF] = 1;
		}
		return frequencies;
		
	}
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for(int i = 0; i < counts.length; i++)
			if(counts[i] > 0)
				pq.add(new HuffNode(i, counts[i]));

		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, (left.myWeight + right.myWeight), left, right);
			pq.add(t);
		}

		HuffNode root = pq.remove();
		return root;
	}
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root,"",encodings);
		return encodings;
	}
	private void codingHelper(HuffNode root, String str, String[] encodings) {
		if(root == null) return;
		if(root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = str;
			if(myDebugLevel >= DEBUG_HIGH)
				System.out.println("encoding" + str);
			return;
		}
		codingHelper(root.myLeft, str + "0", encodings);
		codingHelper(root.myRight, str + "1", encodings);
	}
	
	private void writeCompressedBits(String codings[], 
			BitInputStream in, BitOutputStream out){
		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			if(bits == -1)
				break;
			String code = codings[bits];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
		}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
	}

	private void writeHeader(HuffNode root, BitOutputStream out){
		if(root.myLeft != null && root.myRight != null) {
			out.writeBits(1,0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		else {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
		}
	}

	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bit =in.readBits(BITS_PER_INT);
		if (bit != HUFF_TREE) {
			throw new HuffException("Illegal header starts with" + bit);
		}
		if (bit == -1) throw new HuffException("reading bits fails");
		
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
		
	}
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("reading bits fails");
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0, left, right);	
		}
		else {
			int value = in.readBits(BITS_PER_WORD +1);
			return new HuffNode(value, 0, null, null);
		}
	}
	private void readCompressedBits(HuffNode root, 
			BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bit = in.readBits(1);
			if (bit == -1) throw new HuffException("bad input, no P-E");
			else {
				if (bit == 0) current = current.myLeft;
				else current = current.myRight;
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) break;
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
}