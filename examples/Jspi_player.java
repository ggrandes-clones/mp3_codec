package examples;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.*;

import examples.ShoutcastIcecastInputStream.MetadataListener;

/**
 * Example: java -jar Jspi_player.jar http://mp3.webradio.rockantenne.de:80
 */
public final class Jspi_player implements MetadataListener {

	// start MetadataListener
	@Override
	public void publish(final String metadata) {
		System.out.println( metadata );
	}
	// end MetadataListener

	public static void main(final String[] args) {
		if( args.length != 1 ) {
			System.out.println("Usage:");
			System.out.println("java -jar Jspi_player.jar <Input File[.mp3] or url>");
			System.exit( 0 );
			return;
		}
		AudioInputStream in = null;
		AudioInputStream din = null;
		SourceDataLine audio_out_line = null;
		try {
			if( args[0].toLowerCase().startsWith("http://") ) {
				in = AudioSystem.getAudioInputStream(
						new ShoutcastIcecastInputStream( args[0], true, 1 << 19 )
						.addMetadataListener( new Jspi_player() ) );
			} else {
				in = AudioSystem.getAudioInputStream( new File( args[0] ) );
			}
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
				final DataLine.Info info = new DataLine.Info( SourceDataLine.class, decoded_format );
				if( ! AudioSystem.isLineSupported( info ) ) {
					throw new LineUnavailableException("sorry, the sound format cannot be played");
				}
				audio_out_line = (SourceDataLine) AudioSystem.getLine( info );
				audio_out_line.open();
				audio_out_line.start();
				final byte[] buffer = new byte[4096];
				int readed;
				//
				while( (readed = din.read( buffer, 0, buffer.length )) >= 0 ) {
					audio_out_line.write( buffer, 0, readed );
				}
				//
				audio_out_line.drain();
				System.err.println("Done.");
			}
		} catch(final Exception e) {
			System.err.println( e.getMessage() );
			e.printStackTrace();
		} finally {
			if( audio_out_line != null ) {
				audio_out_line.close();
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
