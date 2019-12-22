package spi.convert;

import java.io.IOException;
import java.io.InputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import libmpghip.Jmpg123;

public final class Mp3_DecodedAudioInputStream extends AudioInputStream {
	private Jmpg123 mDecoder;

	//
	public Mp3_DecodedAudioInputStream(final InputStream stream, final AudioFormat format, final long length) {
		super( stream, format, length );
		try {
			mDecoder = new Jmpg123( format.getSampleSizeInBits() == AudioSystem.NOT_SPECIFIED ? 16 : format.getSampleSizeInBits(),
					format.getEncoding() == Encoding.PCM_SIGNED,
					format.isBigEndian() );
			mDecoder.InitMP3();
			if( mDecoder.open( stream ) >= 0 ) {
				return;
			}
		} catch(final IllegalArgumentException e) {
		}
		mDecoder.ExitMP3();
		mDecoder = null;
	}
	@Override
	public void close() throws IOException {
		if( mDecoder != null ) {
			mDecoder.ExitMP3();
		}
		mDecoder = null;
		super.close();
	}
	@Override
	public boolean markSupported() {
		return false;
	}
	@Override
	public int read() throws IOException {
		final byte[] data = new byte[1];
		if( read( data ) <= 0 ) {// we have a weird situation if read(byte[]) returns 0!
			return -1;
		}
		return ((int) data[0]) & 0xff;
	}
	@Override
	public int read(final byte[] b, final int off, final int len) throws IOException {
		return mDecoder.read( b, off, len );
	}
}
