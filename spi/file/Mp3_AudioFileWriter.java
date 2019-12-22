package spi.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.spi.AudioFileWriter;

import libmp3lame.Jid3tag;
import libmp3lame.Jlame;
import libmp3lame.Jlame_global_flags;

public class Mp3_AudioFileWriter extends AudioFileWriter {
	private final Type[] MPEG = { new Type("MPEG", "mp3") };
	private static final int VBR = 1;
	private static final int CBR = 2;
	private static final int ABR = 3;
	private static final int LAME_BUF_SAMPLE_SIZE = 1152;
	//
	private static final int DEFAULT_BITRATE = VBR;
	private static final float DEFAULT_BITRATE_PARAM = 2f;
	//
	@Override
	public Type[] getAudioFileTypes() {
		return MPEG;
	}

	@Override
	public Type[] getAudioFileTypes(final AudioInputStream stream) {
		return MPEG;
	}

	private static final int write_id3v1_tag(final Jlame_global_flags gf, final OutputStream out) throws IOException {
		final byte mp3buffer[] = new byte[128];

		final int imp3 = Jid3tag.lame_get_id3v1_tag( gf, mp3buffer, mp3buffer.length );
		if( imp3 <= 0 ) {
			return 0;
		}
		if( imp3 > mp3buffer.length ) {
			return 0;
		}
		out.write( mp3buffer, 0, imp3 );
		return imp3;
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

	@Override
	public int write(final AudioInputStream stream, final Type fileType, final OutputStream out) throws IOException {
		if( ! fileType.equals( MPEG[0] ) ) {
			throw new IllegalArgumentException();
		}
		final AudioFormat af = stream.getFormat();
		final int channels = af.getChannels();
		int bitrate_variant = DEFAULT_BITRATE;
		float param = DEFAULT_BITRATE_PARAM;
		if( fileType instanceof EncoderFileFormatType ) {// user input
			final int type = ((EncoderFileFormatType) fileType).mStreamType;
			if( type == EncoderFileFormatType.VBR ) {
				bitrate_variant = VBR;
			}
			if( type == EncoderFileFormatType.CBR ) {
				bitrate_variant = VBR;
			}
			if( type == EncoderFileFormatType.ABR ) {
				bitrate_variant = ABR;
			}
			param = ((EncoderFileFormatType) fileType).mStreamTypeParameter;
		}
		//
		final Jlame_global_flags gf = Jlame.lame_init();
		Jid3tag.id3tag_init( gf );
		// set encoding parameters
		gf.lame_set_num_channels( channels );
		gf.lame_set_out_samplerate( (int)af.getSampleRate() );
		setEncodingParameters( gf, bitrate_variant, param );
		// end set encoding parameters
		gf.lame_set_write_id3tag_automatic( false );
		int ret = Jlame.lame_init_params( gf );
		if( ret < 0 ) {
			return ret;
		}
		//
		final byte mp3buffer[] = new byte[LAME_BUF_SAMPLE_SIZE * 2 * 2];// 2 ch, 2 bytes per sample
		final short pcm[] = new short[2 * LAME_BUF_SAMPLE_SIZE];// 2 ch
		int written = 0;
		int iread;
		while( (iread = stream.read( mp3buffer )) > 0 ) {
			ByteBuffer.wrap( mp3buffer, 0, iread ).order( ByteOrder.LITTLE_ENDIAN ).asShortBuffer().get( pcm, 0, iread >> 1 );
			ret = Jlame.lame_encode_buffer_interleaved( gf, pcm, (iread / channels) >> 1, mp3buffer, 0, mp3buffer.length );

			// was our output buffer big enough?
			if( ret < 0 ) {
				return ret;
			}
			out.write( mp3buffer, 0, ret );
			written += ret;
		}

		ret = Jlame.lame_encode_flush( gf, mp3buffer, mp3buffer.length );

		if( ret < 0 ) {
			return ret;
		}

		out.write( mp3buffer, 0, ret );
		written += ret;
		ret = write_id3v1_tag( gf, out );
		if( ret != 0 ) {
			return -1;
		}
		written += ret;
		Jlame.lame_close( gf );
		return written;
	}

	@Override
	public int write(final AudioInputStream stream, final Type fileType, final File file) throws IOException {
		if( ! fileType.equals( MPEG[0] ) ) {
			throw new IllegalArgumentException();
		}
		FileOutputStream outs = null;
		try {
			outs = new FileOutputStream( file );
			return write( stream, fileType, outs );
		} catch(final IOException e) {
			throw e;
		} finally {
			if( outs != null ) {
				try { outs.close(); } catch( final IOException e ) {}
			}
		}
	}
}
