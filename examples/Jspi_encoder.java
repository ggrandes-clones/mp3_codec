package examples;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.sound.sampled.*;

import spi.file.EncoderFileFormatType;

/**
 * takes a WAV file from an input file and encodes it into a Mp3 output file.
 */
public class Jspi_encoder {
	public static void main(final String[] args) {
		if( args.length != 2 ) {
			System.out.println("Usage:");
			System.out.println("java -jar Jspi_encoder.jar <Input File[.wav]> <Output File[.mp3]>");
			System.exit( 0 );
			return;
		}

		AudioInputStream ins = null;
		FileOutputStream outs = null;
		try {
			ins = AudioSystem.getAudioInputStream( new BufferedInputStream( new FileInputStream( args[0] ) ) );
			final AudioFormat af = ins.getFormat();
			if( ! AudioSystem.isConversionSupported( new AudioFormat.Encoding("MPEG"), af ) ) {
				System.err.println("Sorry, conversation to Mpeg not supported");
				System.exit( 1 );
				return;
			}
			System.out.println("Start encoding " + args[0]);
			outs = new FileOutputStream( args[1] );
			// supports: VBR with quality from 0 to 9.999, CBR, ABR
			AudioSystem.write( ins, new EncoderFileFormatType("MPEG", "mp3", EncoderFileFormatType.VBR, 2f ), outs );
			System.out.println("Encoding complete");
		} catch(final Exception e) {
			System.err.println( e.getMessage() );
			e.printStackTrace();
		} finally {
			if( ins != null ) {
				try { ins.close(); } catch( final IOException e ) {}
			}
			if( outs != null ) {
				try { outs.close(); } catch( final IOException e ) {}
			}
		}
	}
}
