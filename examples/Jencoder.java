package examples;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat.Encoding;

import libmp3lame.JVbrTag;
import libmp3lame.Jid3tag;
import libmp3lame.Jlame;
import libmp3lame.Jlame_global_flags;
import libmp3lame.Jutil;

public final class Jencoder {
	private static final int VBR = 1;
	private static final int CBR = 2;
	private static final int ABR = 3;
	/** maximum size of albumart image (128KB), which affects LAME_MAXMP3BUFFER
	   as well since lame_encode_buffer() also returns ID3v2 tag data */
	private static final int LAME_MAXALBUMART  = (128 * 1024);

	/** maximum size of mp3buffer needed if you encode at most 1152 samples for
	   each call to lame_encode_buffer.  */
	private static final int LAME_MAXMP3BUFFER = (16384 + LAME_MAXALBUMART);
	private static final int LAME_BUF_SAMPLE_SIZE = 1152;
	//
	private static final String getSupportedAudioEncoding() {
		final StringBuilder sb = new StringBuilder("It can read the ");
		final AudioFormat af = new AudioFormat( 48000, 16, 2, true, false );
		// final Encoding[] encodings = ;
		int length = sb.length();
		for( final Encoding e : AudioSystem.getTargetEncodings( af ) ) {
			final String s = e.toString();
			length += s.length();
			if( length > 70 ) {// 70 is max console chars in a line
				sb.append("\n                ");
				length = 16;// space count
			}
			sb.append( s ).append(',').append(' ');
			length += 2;
		}
		if( length + 13 /*"or raw files.\n".length()*/ > 70 ) {
			sb.append("\n                ");
		}
		sb.append("or raw files.\n");
		return sb.toString();
	}

	@SuppressWarnings("boxing")
	private static final int write_xing_frame(final Jlame_global_flags gf, final RandomAccessFile outf, final int offset) {
		final byte mp3buffer[] = new byte[LAME_MAXMP3BUFFER];

		final int imp3 = JVbrTag.lame_get_lametag_frame( gf, mp3buffer, mp3buffer.length );
		if( imp3 <= 0 ) {
			return 0;// nothing to do
		}
		System.out.printf("Writing LAME Tag...");
		if( imp3 > mp3buffer.length ) {
			System.err.printf("Error writing LAME-tag frame, buffer too small: buffer size = %d  frame size = %d\n",
					mp3buffer.length, imp3 );
			return -1;
		}
		try {
			outf.seek( offset );
		} catch(final IOException ie) {
			System.err.println("fatal error: can't update LAME-tag frame!");
			return -1;
		}
		try {
			outf.write( mp3buffer, 0, imp3 );
		} catch(final IOException ie) {
			System.err.println("Error writing LAME-tag");
			return -1;
		}
		System.out.printf("done\n");
		return imp3;
	}

	@SuppressWarnings("boxing")
	private static final int write_id3v1_tag(final Jlame_global_flags gf, final RandomAccessFile outf) {
		final byte mp3buffer[] = new byte[128];

		final int imp3 = Jid3tag.lame_get_id3v1_tag( gf, mp3buffer, mp3buffer.length );
		if( imp3 <= 0 ) {
			return 0;
		}
		if( imp3 > mp3buffer.length ) {
			System.err.printf("Error writing ID3v1 tag, buffer too small: buffer size = %d  ID3v1 size = %d\n",
							mp3buffer.length, imp3 );
			return 0;// not critical
		}
		try {
			outf.write( mp3buffer, 0, imp3 );
		} catch(final IOException ie) {
			System.err.println("Error writing ID3v1 tag");
			return 1;
		}
		return 0;
	}
	private static final void setEncodingParameters(final Jlame_global_flags gfp, final int vbr_cbr_abr, final float br_param) {
		if( vbr_cbr_abr == VBR ) {
			if( gfp.lame_get_VBR() == Jlame.vbr_off ) {
				gfp.lame_set_VBR( Jlame.vbr_default );
			}
			gfp.lame_set_VBR_quality( br_param );

		} else if( vbr_cbr_abr == CBR ) {
			gfp.lame_set_VBR( Jlame.vbr_off );
			gfp.lame_set_brate( (int)br_param );
			gfp.lame_set_VBR_min_bitrate_kbps( gfp.lame_get_brate() );
		} else if( vbr_cbr_abr == ABR ) {
			int m = (int)br_param;
			if( m >= 8000 ) {
				m = (m + 500) / 1000;
			}
			if( m > 320 ) {
				m = 320;
			}
			if( m < 8 ) {
				m = 8;
			}
			gfp.lame_set_VBR( Jlame.vbr_abr );
			gfp.lame_set_VBR_mean_bitrate_kbps( m );
		}
		//gfp.lame_set_free_format( true );
	}
	@SuppressWarnings("boxing")
	public static final void main(final String[] args) {
		if( args.length != 2 ) {
			System.out.println("Usage:");
			System.out.println("java -jar Jencoder.jar <Input File[.wav]> <Output File[.mp3]>");
			System.out.print( getSupportedAudioEncoding() );
			System.exit( 0 );
			return;
		}
		//
		AudioInputStream in = null;
		AudioInputStream din = null;
		RandomAccessFile outf = null;
		try {
			in = AudioSystem.getAudioInputStream( new File( args[0] ) );
			if( in != null ) {
				final AudioFormat in_format = in.getFormat();
				final int channels = in_format.getChannels();
				final AudioFormat decoded_format = new AudioFormat(
						AudioFormat.Encoding.PCM_SIGNED,
						in_format.getSampleRate(),
						16, channels, channels * (16 / 8),// 16 bit
						in_format.getSampleRate(),
						false );
				din = AudioSystem.getAudioInputStream( decoded_format, in );
				//
				final Jlame_global_flags gf = Jlame.lame_init();
				Jid3tag.id3tag_init( gf );
				// set encoding parameters
				if( Jutil.SmpFrqIndex( (int)in_format.getSampleRate() ) < 0 ) {
					System.err.printf("Unsupporting sample rate: %d\n", (int)in_format.getSampleRate());
					System.exit( 1 );
					return;
				}
				gf.lame_set_num_channels( channels );
				gf.lame_set_out_samplerate( (int)in_format.getSampleRate() );
				setEncodingParameters( gf, VBR, 2f );
				// end set encoding parameters
				// turn off automatic writing of ID3 tag data into mp3 stream
				// we have to call it before 'lame_init_params', because that
				// function would spit out ID3v2 tag data.
				gf.lame_set_write_id3tag_automatic( false );
				// Now that all the options are set, lame needs to analyze them and
				// set some more internal options and check for problems
				final int ret = Jlame.lame_init_params( gf );
				if( ret < 0 ) {
					System.err.println("Fatal error during initialization");
					System.exit( 1 );
					return;
				}
				//
				final byte mp3buffer[] = new byte[LAME_BUF_SAMPLE_SIZE * 2 * 2];// 2 ch, 2 bytes per sample
				final short pcm[] = new short[2 * LAME_BUF_SAMPLE_SIZE];// 2 ch
				//
				System.out.println("Start encoding " + args[0]);
				outf = new RandomAccessFile( args[1], "rw" );
				// encode until we hit eof
				int iread;
				while( (iread = din.read( mp3buffer )) > 0 ) {
					ByteBuffer.wrap( mp3buffer, 0, iread ).order( ByteOrder.LITTLE_ENDIAN ).asShortBuffer().get( pcm, 0, iread >> 1 );
					final int imp3 = Jlame.lame_encode_buffer_interleaved( gf, pcm, (iread / channels) >> 1, mp3buffer, 0, mp3buffer.length );

					// was our output buffer big enough?
					if( imp3 < 0 ) {
						if( imp3 == -1 ) {
							System.err.println("Mp3 buffer is not big enough...");
						} else {
							System.err.printf("Mp3 internal error:  error code = %d\n", imp3 );
						}
						System.exit( 1 );
						return;
					}
					try {
						outf.write( mp3buffer, 0, imp3 );
					} catch(final IOException ie) {
						System.err.println("Error writing mp3 output");
						System.exit( 1 );
						return;
					}
				}

				int imp3 = Jlame.lame_encode_flush( gf, mp3buffer, mp3buffer.length );

				if( imp3 < 0 ) {
					if( imp3 == -1 ) {
						System.err.println("Mp3 buffer is not big enough...");
					} else {
						System.err.printf("Mp3 internal error:  error code = %d\n", imp3);
					}
					System.exit( 1 );
					return;
				}

				try {
					outf.write( mp3buffer, 0, imp3 );
				} catch(final IOException ie) {
					System.err.println("Error writing mp3 output");
					System.exit( 1 );
					return;
				}
				/*if( Jparse.global_writer.flush_write ) {
					outf.flush();
				}*/
				imp3 = write_id3v1_tag( gf, outf );
				if( imp3 != 0 ) {
					System.exit( 1 );
					return;
				}
				write_xing_frame( gf, outf, 0/*id3v2_size*/ );
				//
				Jlame.lame_close( gf );
				System.out.println("Done.");
			}
		} catch(final Exception e) {
			System.err.println( e.getMessage() );
			e.printStackTrace();
		} finally {
			if( outf != null ) {
				try { outf.close(); } catch( final IOException e ) {}
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
