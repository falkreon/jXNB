package blue.endless.jxnb;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public class XNBInputStream {
	private static final int MAGIC_XBOX =
			'X' << 24 |
			'N' << 16 |
			'B' << 8 |
			'x';
	
	private static final int MAGIC_WINDOWS =
			'X' << 24 |
			'N' << 16 |
			'B' << 8 |
			'w';
	
	private static final int MAGIC_WINDOWS_PHONE_7 =
			'X' << 24 |
			'N' << 16 |
			'B' << 8 |
			'm';
	
	private static final int FLAG_HIDEF = 0x01;
	private static final int FLAG_COMPRESSED = 0x80;
	
	private static final String READER_TEXTURE2D = "Microsoft.Xna.Framework.Content.Texture2DReader";
	
	private InputStream underlying;
	
	public XNBInputStream(InputStream in) throws IOException {
		underlying = in;
		DataInputStream din = new DataInputStream(in);
		int magic = din.readInt();
		switch(magic) {
		case MAGIC_XBOX:
			System.out.println("xbox XNB");
			break;
		case MAGIC_WINDOWS:
			System.out.println("windows XNB");
			break;
		case MAGIC_WINDOWS_PHONE_7:
			System.out.println("windows phone 7 XNB");
			break;
		default:
			throw new IOException("Stream was not an XNB file or was an unknown type.");
		}
		
		int formatVersion = din.readByte();
		if (formatVersion!=5) throw new IOException("Can only parse version 5 XNB");
		
		int flags = din.read() & 0xFF;
		System.out.println("FLAGS: "+Integer.toHexString(flags & 0xFF));
		if ((flags & FLAG_HIDEF) != 0) System.out.println("  HIDEF");
		boolean compressed = (flags & FLAG_COMPRESSED) != 0;
		if (compressed) System.out.println("  COMPRESSED");
		
		
		int compressedFileSize = readInt32(din) - 10;
		
		
		
		
		if (compressed) {
			compressedFileSize -= 4;
			int uncompressedFileSize = readInt32(din);
			System.out.println("COMPRESSED DATA! Compressed: "+compressedFileSize+", Uncompressed: "+uncompressedFileSize);
			
			byte[] compressedData = readReliableByteArray(in, compressedFileSize);
			ByteBuffer compressedBuf = ByteBuffer.wrap(compressedData);
			byte[] uncompressedData = new byte[uncompressedFileSize];
			ByteBuffer uncompressedBuf = ByteBuffer.wrap(uncompressedData);
			new LzxDecoder().decompress(compressedBuf, compressedFileSize, uncompressedBuf, uncompressedFileSize);
			
			
			System.out.println("Compressed data: "+printByteArray(compressedData));
			System.out.println();
			System.out.println("Decompressed data: "+printByteArray(uncompressedData));
			
			underlying = new ByteArrayInputStream(uncompressedData);
			DataInputStream din2 = new DataInputStream(underlying);
			din.close();
			din = din2;
		}
		
		int typeEntries = read7BitEncodedInt(din);
		List<String> typeEntryReaders = new ArrayList<>();
		System.out.println(""+typeEntries+" type entries");
		for(int i=0; i<typeEntries; i++) {
			String typeName = readString(din);
			int version = readInt32(din);
			System.out.println("  type: "+typeName+", version: "+version);
			int firstComma = typeName.indexOf(',');
			String typeReaderName = (firstComma!=-1) ? typeName.substring(0, firstComma) : typeName;
			typeEntryReaders.add(typeReaderName);
		}
		
		int sharedResourceEntries = read7BitEncodedInt(din);
		System.out.println(""+sharedResourceEntries+" shared resource entries (used to handle reference cycles)");
		
		//DESERIALIZE BASE OBJECT
		
		Object result = readObject(din, typeEntryReaders);
		//TODO: Use this object somehow.
		
		//byte[] str = readReliableByteArray(din, 10);
		//String test = new String(str, StandardCharsets.UTF_8);
		//System.out.println("["+test+"]");
		
		for(int i=0; i<sharedResourceEntries; i++) {
			
		}
		
	}

	public void close() throws IOException {
		underlying.close();
	}
	
	private static Object readObject(DataInputStream in, List<String> typeReaders) throws IOException {
		int typeId = read7BitEncodedInt(in);
		if (typeId==0) {
			System.out.println("Object: NULL");
			return null;
		}
		
		typeId--;
		if (typeId<0 || typeId>=typeReaders.size()) {
			throw new IOException("Type ID not in type entries list");
		}
		
		String reader = typeReaders.get(typeId);
		switch(reader) {
		case READER_TEXTURE2D:
			int imageType = readInt32(in);
			System.out.println("Image Type: "+imageType);
			int width = readInt32(in);
			int height = readInt32(in);
			int mips = readInt32(in);
			System.out.println("Size: "+width+"x"+height+", "+mips+" mips.");
			
			int dataSize = readInt32(in);
			System.out.println("Data size: "+dataSize);
			if (imageType==0 && dataSize == (width*height*4)) {
				System.out.println("Color Image, Data size matches. Decoding image.");
				BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				for(int y=0; y<height; y++) {
					for(int x=0; x<width; x++) {
						int pixel = readInt32(in);
						result.setRGB(x, y, pixel);
					}
				}
				
				//ImageIO.write(result, "png", new File("out.png"));
				return result;
			}
			
			return null;
		default:
			throw new IOException("Unknown reader type '"+reader+"'. Object is of unknown length.");
		}
	}
	
	//TODO: faster impl
	private static byte[] readReliableByteArray(InputStream in, int len) throws IOException {
		byte[] result = new byte[len];
		for(int i=0; i<len; i++) {
			int cur = in.read();
			if (cur==-1) throw new IOException("EOF found while reading a byte[] ("+(i-1)+" bytes read)");
			result[i] = (byte) (cur & 0xFF);
		}
		return result;
	}
	
	public static int read7BitEncodedInt(InputStream in) throws IOException {
		int result = 0;
		int bitsRead = 0;
		int value;

		do {
			value = in.read();
			//result <<= 7;
			//result |= (value & 0x7f);
			result |= (value & 0x7f) << bitsRead;
			bitsRead += 7;
		} while ((value & 0x80) != 0);

		return result;
	}
	
	public static String readString(InputStream in) throws IOException {
		int len = read7BitEncodedInt(in);
		//System.out.println("    len: "+len);
		if (len==0) return "";
		byte[] str = readReliableByteArray(in, len);
		return new String(str, StandardCharsets.UTF_8); //XNB is smart enough to use UTF8 as their serialized string format
	}
	
	public static int readInt32(InputStream in) throws IOException {
		int a = in.read();
		int b = in.read();
		int c = in.read();
		int d = in.read();
		if (a==-1 || b==-1 || c==-1 || d==-1) throw new IOException("Found EOF while reading a uint32");
		
		a &= 0xFF;
		b &= 0xFF;
		c &= 0xFF;
		d &= 0xFF;
		return a | (b << 8) | (c << 16) | (d << 24);
	}
	
	private static final int MAX_ARRAY_PRINT = 20;
	private static String printByteArray(byte[] b) {
		StringBuilder result = new StringBuilder();
		result.append("[ ");
		
		for(int i=0; i<Math.min(b.length, MAX_ARRAY_PRINT); i++) {
			result.append(Integer.toHexString(b[i] & 0xFF));
			if (i<b.length-1) result.append(", ");
		}
		
		if (b.length>0) result.append(' ');
		if (b.length>MAX_ARRAY_PRINT) result.append("... ");
		result.append(']');
		
		return result.toString();
	}
}
