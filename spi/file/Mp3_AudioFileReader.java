package spi.file;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;

import libmpghip.Jmpg123;
import spi.convert.Mp3_FormatConversionProvider;

public class Mp3_AudioFileReader extends AudioFileReader {
	// there is real problem: decoder must process all metadata block. this block can have huge size.
	private static final int MAX_BUFFER = 65536 * 8;

	@Override
	public AudioFileFormat getAudioFileFormat(final InputStream stream) throws UnsupportedAudioFileException, IOException {
		final Jmpg123 decoder = new Jmpg123( 16, true, false );
		decoder.InitMP3();
		if( decoder.open( stream ) < 0 ) {
			decoder.ExitMP3();
			throw new UnsupportedAudioFileException();
		}
		final int rate = decoder.getSampleRate();
		final AudioFormat af = new AudioFormat( Mp3_FormatConversionProvider.ENCODING,
				rate, AudioSystem.NOT_SPECIFIED, decoder.getChannelCount(), 1, rate, false );
		final AudioFileFormat aff = new AudioFileFormat(
				new AudioFileFormat.Type("MPEG", ""), af, AudioSystem.NOT_SPECIFIED );
		return aff;
	}

	@Override
	public AudioFileFormat getAudioFileFormat(final URL url)
			throws UnsupportedAudioFileException, IOException {

		InputStream is = null;
		try {
			is = url.openStream();
			return getAudioFileFormat( is );
		} catch(final UnsupportedAudioFileException e) {
			throw e;
		} catch(final IOException e) {
			throw e;
		} finally {
			if( is != null ) {
				try{ is.close(); } catch(final IOException e) {}
			}
		}
	}

	@Override
	public AudioFileFormat getAudioFileFormat(final File file)
			throws UnsupportedAudioFileException, IOException {

		InputStream is = null;
		try {
			is = new BufferedInputStream( new FileInputStream( file ), MAX_BUFFER );
			return getAudioFileFormat( is );
		} catch(final UnsupportedAudioFileException e) {
			throw e;
		} catch(final IOException e) {
			throw e;
		} finally {
			if( is != null ) {
				try{ is.close(); } catch(final IOException e) {}
			}
		}
	}

	@Override
	public AudioInputStream getAudioInputStream(final InputStream stream)
			throws UnsupportedAudioFileException, IOException {

		// doc says: If the input stream does not support this, this method may fail with an IOException.
		// if( ! stream.markSupported() ) stream = new BufferedInputStream( stream, Jformat.FLAC__MAX_BLOCK_SIZE * 2 );// possible resources leak
		try {
			stream.mark( MAX_BUFFER );
			final AudioFileFormat af = getAudioFileFormat( stream );
			stream.reset();// to start read header again
			return new AudioInputStream( stream, af.getFormat(), af.getFrameLength() );
		} catch(final UnsupportedAudioFileException e) {
			stream.reset();
			throw e;
		} catch(final IOException e) {
			System.out.println( e.getMessage() );
			stream.reset();
			throw e;
		}
	}

	@Override
	public AudioInputStream getAudioInputStream(final URL url)
			throws UnsupportedAudioFileException, IOException {

		InputStream is = null;
		try {
			is = url.openStream();
			return getAudioInputStream( is );
		} catch(final UnsupportedAudioFileException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		} catch(final IOException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		}
	}

	@Override
	public AudioInputStream getAudioInputStream(final File file)
			throws UnsupportedAudioFileException, IOException {

		BufferedInputStream is = null;
		try {
			is = new BufferedInputStream( new FileInputStream( file ), MAX_BUFFER );
			return getAudioInputStream( is );
		} catch(final UnsupportedAudioFileException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		} catch(final IOException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		}
	}
}
