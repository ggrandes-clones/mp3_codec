package examples;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import libmpghip.Jmpg123;

/**
 * Decoder is using java extension
 */
public final class Jdecoder2 {
	private static final byte[] PCM_HEADER = {
			'R', 'I', 'F', 'F',// RIFF
			0, 0, 0, 0,// RIFF size = full size - 8
			'W', 'A', 'V', 'E', 'f', 'm', 't', ' ',// WAVEfmt
			0x10, 0, 0, 0,// Ext
			1, 0,// Format Tag
			1, 0,// number of channels
			0x40, 0x1f, 0x00, 0x00,// Samples per seconds
			(byte)0x80, 0x3e, 0x00, 0x00,// Avg Bytes per seconds
			4, 0,// BLock align
			16, 0,// Bits per sample
			'd', 'a', 't', 'a',// data
			0, 0, 0, 0// data size = full size - 44
		};
	private static final int RIFF_SIZE_OFFSET = 4;
	private static final int DATA_SIZE_OFFSET = 40;
	/**
	 * Value to byte array in little endian order.
	 * @param value value
	 * @param byteCount how many bytes in the value be used
	 * @param data array
	 * @param offset start offset to write data
	 */
	private static final void valueToBytes(final int value, final int byteCount, final byte[] data, int offset) {
		final int count = byteCount << 3;
		for( int i = 0; i < count; i += 8 ) {
			data[offset++] = (byte)(value >> i);
		}
	}
	/**
	 * Write sound sound format data to PCM WAV header
	 * @param sampleRate sample rate
	 * @param bitsPerSample bits per one sample
	 * @param channels count of channels
	 */
	private static final void setSoundFormat(final int sampleRate, final int bitsPerSample, final int channels) {
		valueToBytes( channels, 2, PCM_HEADER, 22 );// Channels
		valueToBytes( sampleRate, 4, PCM_HEADER, 24 );// Sample rate
		valueToBytes( sampleRate * channels * (bitsPerSample >> 3), 4, PCM_HEADER, 28 );// Avg Bytes per seconds
		valueToBytes( (bitsPerSample >> 3) * channels, 2, PCM_HEADER, 32 );// BLock align
		valueToBytes( bitsPerSample, 2, PCM_HEADER, 34 );// Bits per sample
	}
	/**
	 * Write data length to PCM WAV header
	 * @param bytesWritten data length in bytes
	 */
	private static final void setDataLength(final long bytesWritten) {
		int size = (int)bytesWritten;
		valueToBytes( size, 4, PCM_HEADER, DATA_SIZE_OFFSET );
		size += DATA_SIZE_OFFSET - RIFF_SIZE_OFFSET;
		valueToBytes( size, 4, PCM_HEADER, RIFF_SIZE_OFFSET );
	}
	/**
	 * main.
	 * @param args path to mpeg input file and path to a decoded file.
	 */
	public static final void main(final String[] args) {
		if( args.length != 2 ) {
			System.out.println("Usage:");
			System.out.println("java -jar Jdecoder.jar <Input File[.mp3]> <Output File[.wav]>");
			System.exit( 0 );
			return;
		}
		final Jmpg123 decoder = new Jmpg123( 16, true, false );// get result as 16 bit, signed, little endian
		decoder.InitMP3();
		FileInputStream in = null;
		RandomAccessFile out = null;
		try {
			in = new FileInputStream( args[0] );
			decoder.open( in );
			//
			System.out.println("Start decoding " + args[0]);
			out = new RandomAccessFile( args[1], "rw" );
			setSoundFormat( decoder.getSampleRate(), 16, decoder.getChannelCount() );
			out.write( PCM_HEADER );
			//
			long wavsize = 0;
			final byte[] buff = new byte[4096];// must be integral byte count to get desired samples
			int read;
			while( (read = decoder.read( buff, 0, 4096 )) >= 0 ) {
				out.write( buff, 0, read );
				wavsize += read;
			}
			//
			if( wavsize <= 0 ) {
				System.err.println("WAVE file contains 0 PCM samples");
			} else if( wavsize > 0xFFFFFFD0L / (decoder.getChannelCount() << 1) ) {// 2 bytes per int16
				System.err.println("Very huge WAVE file, can't set filesize accordingly");
				wavsize = 0xFFFFFFD0L;
			}
			setDataLength( wavsize );
			out.seek( 0 );
			out.write( PCM_HEADER );
			System.out.println("Done.");
		} catch(final Exception e) {
			e.printStackTrace();
			System.exit( 1 );
			return;
		} finally {
			if( out != null ) {
				try { out.close(); } catch( final IOException e ) {}
			}
			if( in != null ) {
				try { in.close(); } catch( final IOException e ) {}
			}
			decoder.ExitMP3();
		}
		System.exit( 0 );
		return;
	}
}
