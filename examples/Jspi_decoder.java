package examples;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import javax.sound.sampled.*;

public final class Jspi_decoder {
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
	//
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
	//
	public static void main(final String[] args) {
		if( args.length != 2 ) {
			System.out.println("Usage:");
			System.out.println("java -jar Jspi_player.jar <Input File[.mp3]> <Output File[.wav]>");
			System.exit( 0 );
			return;
		}
		AudioInputStream in = null;
		AudioInputStream din = null;
		RandomAccessFile out = null;
		try {
			in = AudioSystem.getAudioInputStream( new File( args[0] ) );
			if( in != null ) {
				final AudioFormat in_format = in.getFormat();
				final int channels = in_format.getChannels();
				final AudioFormat decoded_format = new AudioFormat(
						AudioFormat.Encoding.PCM_SIGNED,
						in_format.getSampleRate(),
						16, channels, channels * (16 / 8),
						in_format.getSampleRate(),
						false );
				din = AudioSystem.getAudioInputStream( decoded_format, in );
				//
				System.out.println("Start decoding " + args[0]);
				//
				out = new RandomAccessFile( args[1], "rw" );
				setSoundFormat( (int)in_format.getSampleRate(), 16, channels );
				out.write( PCM_HEADER );
				//
				int wavsize = 0;
				final byte[] buffer = new byte[4096];
				int readed;
				while( (readed = din.read( buffer, 0, buffer.length )) >= 0 ) {
					out.write( buffer, 0, readed );
					wavsize += readed;
				}
				//
				setDataLength( wavsize );
				out.seek( 0 );
				out.write( PCM_HEADER );
				//
				System.out.print("Done.\n");
			}
		} catch(final Exception e) {
			System.err.println( e.getMessage() );
			e.printStackTrace();
		} finally {
			if( out != null ) {
				try { out.close(); } catch( final IOException e ) {}
			}
			if( din != null ) {
				try { din.close(); } catch( final IOException e ) {}
			}
			if( in != null ) {
				try { in.close(); } catch( final IOException e ) {}
			}
		}
	}
}
