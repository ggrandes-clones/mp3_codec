package examples;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import libmpghip.Jmpg123;
import libmpghip.Jmpstr_tag;

/**
 * Decoder is using standart Mpg123 library interface
 */
public final class Jdecoder extends Jmpstr_tag {
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
	//
	private static final int FORMAT_UNKNOWN = 0;
	private static final int FORMAT_MP1 = 1;
	private static final int FORMAT_MP2 = 2;
	private static final int FORMAT_MP3 = 3;
	//
	private static final long MAX_U_32_NUM = 0xFFFFFFFFL;
	private static final int ENCDELAY = 576;
	//
	private static final byte sAbl2[] = { 0, 7, 7, 7, 0, 7, 0, 0, 0, 0, 0, 8, 8, 8, 8, 8 };
	//
	private static final int OUT_SIZE = 2 * 1152;
	private final short mOutClipped[] = new short[OUT_SIZE];
	//
	private byte[] mId3v2TagBuff = null;
	//
	private int mInputFormat = FORMAT_UNKNOWN;
	/** 1 if header was parsed and following data was computed */
	private boolean mIsHeaderParsed = false;
	/** number of channels */
	private int mNumChannels = 0;
	/** sample rate */
	private int mSampleRate = 0;
	/** bitrate */
	private int mBitrate = 0;
	/* this data is only computed if mpglib detects a Xing VBR header */
	/** number of samples in mp3 file. */
	private long mTotalNumSamples = 0;
	// pcm buffer
	/** buffer for each channel */
	private short mBuffer[] = null;
	/** number samples allocated */
	private int mNumSamplesAllocated = 0;
	/** number samples used */
	private int mNumSamplesUsed = 0;
	/** number samples to ignore at the beginning */
	private int mSkipStart = 0;
	/** number samples to ignore at the end */
	private int mSkipEnd = 0;
	// end pcm buffer
	/**
	 * Default decoder constructor
	 */
	private Jdecoder() {
	}
	//
	private final boolean isSyncwordMp123(final byte[] header) {
		if( (header[0] & 0xFF) != 0xFF ) {
			return false;//* first 8 bits must be '1'
		}
		if( (header[1] & 0xE0) != 0xE0 ) {
			return false;// next 3 bits are also
		}
		if( (header[1] & 0x18) == 0x08) {
			return false;// no MPEG-1, -2 or -2.5
		}
		switch( header[1] & 0x06 ) {
		default:
		case 0x00:// illegal Layer
			mInputFormat = FORMAT_UNKNOWN;
			return false;

		case 0x02:// Layer3
			mInputFormat = FORMAT_MP3;
			break;

		case 0x04:// Layer2
			mInputFormat = FORMAT_MP2;
			break;

		case 0x06:// Layer1
			mInputFormat = FORMAT_MP1;
			break;
		}
		if( (header[1] & 0x06) == 0x00 ) {
			return false;// no Layer I, II and III
		}
		if( (header[2] & 0xF0) == 0xF0 ) {
			return false;// bad bitrate
		}
		if( (header[2] & 0x0C) == 0x0C ) {
			return false;// no sample frequency with (32,44.1,48)/(1,2,4)
		}
		if( (header[1] & 0x18) == 0x18 && (header[1] & 0x06) == 0x04 && ((sAbl2[((int)header[2] & 0xff) >> 4] & (1 << (((int)header[3] & 0xff) >> 6))) != 0) ) {
			return false;
		}
		if( (header[3] & 3) == 2 ) {
			return false;// reserved enphasis mode
		}
		return true;
	}
	private static final int getLenOfId3v2Tag(final byte[] buf, int offset) {
		final int b0 = (int)buf[offset++] & 127;
		final int b1 = (int)buf[offset++] & 127;
		final int b2 = (int)buf[offset++] & 127;
		final int b3 = (int)buf[offset  ] & 127;
		return (((((b0 << 7) + b1) << 7) + b2) << 7) + b3;
	}
	/**
	 * For lame_decode:  return code
	 * -1     error
	 *  0     ok, but need more data before outputing any samples
	 *  n     number of samples output.  either 576 or 1152 depending on MP3 file.
	 *
	 */
    private final int decodeHeaders(final byte[] buffer, final int len,
		final short[] p, final int psize)
	{
		final int processed_mono_samples[] = new int[1];// java: processed_bytes changed to processed_mono_samples

		mIsHeaderParsed = false;

		final int ret = decodeMP3( buffer, len, p, psize, processed_mono_samples );
		/* three cases:
		 * 1. headers parsed, but data not complete
		 *       pmp.header_parsed==1
		 *       pmp.framesize=0
		 *       pmp.fsizeold=size of last frame, or 0 if this is first frame
		 *
		 * 2. headers, data parsed, but ancillary data not complete
		 *       pmp.header_parsed==1
		 *       pmp.framesize=size of frame
		 *       pmp.fsizeold=size of last frame, or 0 if this is first frame
		 *
		 * 3. frame fully decoded:
		 *       pmp.header_parsed==0
		 *       pmp.framesize=0
		 *       pmp.fsizeold=size of frame (which is now the last frame)
		 *
		 */
		if( this.header_parsed || this.fsizeold > 0 || this.framesize > 0 ) {
			mIsHeaderParsed = true;
			mNumChannels = this.fr.stereo;
			mSampleRate = freqs[this.fr.sampling_frequency];

	        // free format, we need the entire frame before we can determine
	        // the bitrate.  If we haven't gotten the entire frame, bitrate=0
			if( this.fsizeold > 0 ) {
				mBitrate = (int)(8 * (4 + this.fsizeold) * mSampleRate /
						(1.e3 * this.framesize) + 0.5);
			} else if( this.framesize > 0 ) {
				mBitrate = (int)(8 * (4 + this.framesize) * mSampleRate /
						(1.e3 * this.framesize) + 0.5);
			} else {
				mBitrate = tabsel_123[this.fr.lsf][this.fr.lay - 1][this.fr.bitrate_index];
			}

			if( this.num_frames > 0 ) {
				// Xing VBR header found and num_frames was set
				mTotalNumSamples = this.framesize * this.num_frames;
			}
		}

		switch( ret ) {
		case Jmpg123.MP3_OK:
			return processed_mono_samples[0];

		case Jmpg123.MP3_NEED_MORE:
			return 0;
		}

		return -1;
	}
	@SuppressWarnings("boxing")
	private final int init(final RandomAccessFile fd) throws IOException {
		final byte buf[] = new byte[100];
		int len = 4;
		if( fd.read( buf, 0, len ) != len ) {
			throw new IOException("Read returns not len bytes");
		}
		while( buf[0] == 'I' && buf[1] == 'D' && buf[2] == '3' ) {
			len = 6;
			if( fd.read( buf, 4, len ) != len ) {
				throw new IOException("Read returns not len bytes");
			}
			len = getLenOfId3v2Tag( buf, 6 );
			if( mId3v2TagBuff == null ) {
				mId3v2TagBuff = new byte[ 10 + len ];
				System.arraycopy( buf, 0, mId3v2TagBuff, 0, 10 );
				if( fd.read( mId3v2TagBuff, 10, len ) != len ) {
					throw new IOException("Read returns not len bytes");
				}
			}
			len = 4;
			if( fd.read( buf, 0, len ) != len ) {
				throw new IOException("Read returns not len bytes");
			}
		}
		if( buf[0] == 'A' && buf[1] == 'i' && buf[2] == 'D' && buf[3] == '\1' ) {
			if( fd.read( buf, 0, 2 ) != 2 ) {
				throw new IOException("Can not read 2 bytes");
			}
			final int aid_header = ((int)buf[0] & 0xff) + (((int)buf[1] & 0xff) << 8);
			System.out.printf("Album ID found. length = %d\n", aid_header );
			// skip rest of AID, except for 6 bytes we have already read
			fd.seek( aid_header - 6 + fd.getFilePointer() );
			// read 4 more bytes to set up buffer for MP3 header check
			if( fd.read( buf, 0, len ) != len ) {
				throw new IOException("Read returns not len bytes");
			}
		}
		len = 3;
		while( ! isSyncwordMp123( buf ) ) {
			buf[0] = buf[1];
			buf[1] = buf[2];
			buf[2] = buf[3];
			if( fd.read( buf, len, 1 ) != 1 ) {
				throw new IOException("Can not read 1 byte");
			}
		}
		len++;
		boolean freeformat = false;
		if( (buf[2] & 0xf0) == 0 ) {
			System.out.println("Input file is freeformat.");
			freeformat = true;
		}
		/* now parse the current buffer looking for MP3 headers.
		  (as of 11/00: mpglib modified so that for the first frame where
		  headers are parsed, no data will be decoded.
		  However, for freeformat, we need to decode an entire frame,
		  so mp3data.bitrate will be 0 until we have decoded the first
		  frame.  Cannot decode first frame here because we are not
		  yet prepared to handle the output. */
		int ret = decodeHeaders( buf, len, mOutClipped, OUT_SIZE );
		if( -1 == ret ) {
			return -1;
		}
		// repeat until we decode a valid mp3 header.
		while( ! mIsHeaderParsed ) {
			len = fd.read( buf );
			if( len != buf.length ) {
				throw new IOException("Read returns not len bytes");
			}
			ret = decodeHeaders( buf, len, mOutClipped, OUT_SIZE );
			if( -1 == ret ) {
				return -1;
			}
		}
		if( mBitrate == 0 && ! freeformat ) {
			throw new IOException("fail to sync...");
		}
		// if totalframes > 0, mpglib found a Xing VBR header and computed nsamp & totalframes
		if( this.num_frames <= 0 ) {
			// set as unknown.  Later, we will take a guess based on file size ant bitrate
			mTotalNumSamples = MAX_U_32_NUM;
		}
		return 0;
	}

	@SuppressWarnings("boxing")
	private final void printInputFormat() {
		System.out.printf("\rinput: %d Hz, %d channel%s, ",
				this.mSampleRate, this.mNumChannels, this.mNumChannels != 1 ? "s" : "");
		final int v_main = 2 - (this.mSampleRate > 22050 && this.mSampleRate <= 48000 ? 1 : 0);
		final String v_ex = this.mSampleRate < 16000 ? ".5" : "";
		switch( this.mInputFormat ) {
		case FORMAT_MP3:
			System.out.printf("MPEG-%d%s Layer %s\n", v_main, v_ex, "III");
			break;
		case FORMAT_MP2:
			System.out.printf("MPEG-%d%s Layer %s\n", v_main, v_ex, "II");
			break;
		case FORMAT_MP1:
			System.out.printf("MPEG-%d%s Layer %s\n", v_main, v_ex, "I");
			break;
		default:
			System.out.printf("unknown\n");
			break;
		}
	}

	private final int addPcmBuffer(final short[] a, final int read) {
		if( read < 0 ) {
			return mNumSamplesUsed - mSkipEnd;
		}
		if( mSkipStart >= read ) {
			mSkipStart -= read;
			return mNumSamplesUsed - mSkipEnd;
		}
		final int a_want = read - mSkipStart;
		if( a_want > 0 ) {
			final int b_need = (mNumSamplesUsed + a_want);
			if( mNumSamplesAllocated < b_need ) {
				mNumSamplesAllocated = b_need;
				mBuffer = mBuffer == null ? new short[b_need] : Arrays.copyOf( mBuffer, b_need );
			}
			System.arraycopy( a, mSkipStart, mBuffer, mNumSamplesUsed, a_want );
			mNumSamplesUsed = b_need;
		}
		mSkipStart = 0;
		return mNumSamplesUsed - mSkipEnd;
	}

	private final int takePcmBuffer(final short[] a, int take, final int mm) {
		if( take > mm ) {
			take = mm;
		}
		if( take > 0 ) {
			System.arraycopy( mBuffer, 0, a, 0, take );
			mNumSamplesUsed -= take;
			if( mNumSamplesUsed < 0 ) {
				mNumSamplesUsed = 0;
				return take;
			}
			System.arraycopy( mBuffer, take, mBuffer, 0, mNumSamplesUsed );
		}
		return take;
	}

	/**
	 * main.
	 * @param args path to mpeg input file and path to a decoded file.
	 */
	@SuppressWarnings("boxing")
	public static final void main(final String[] args) {
		if( args.length != 2 ) {
			System.out.println("Usage:");
			System.out.println("java -jar Jdecoder.jar <Input File[.mp3]> <Output File[.wav]>");
			System.exit( 0 );
			return;
		}
		RandomAccessFile rd = null;
		RandomAccessFile out = null;
		//
		final Jdecoder decoder = new Jdecoder();
		decoder.InitMP3();
		//
		try {
			rd = new RandomAccessFile( args[0], "r" );
			if( -1 == decoder.init( rd ) ) {
				System.err.printf("Error reading headers in mp3 input file %s.\n", args[0] );
				rd.close();
				System.exit( 1 );
				return;
			}

			if( decoder.mNumChannels != 2 && decoder.mNumChannels != 1 ) {
				System.err.printf("Unsupported number of channels: %d\n", decoder.mNumChannels );
				rd.close();
				System.exit( 1 );
				return;
			}
			if( decoder.mTotalNumSamples == MAX_U_32_NUM ) {
				try {
					final long flen = rd.length();// try to figure out num_samples
					// try file size, assume 2 bytes per sample
					if( decoder.mBitrate > 0 ) {
						final double totalseconds =
								(flen * 8.0 / (1000.0 * decoder.mBitrate) );
						final long tmp_num_samples =
								(long) (totalseconds * decoder.mSampleRate );

						decoder.mTotalNumSamples = tmp_num_samples;
					}
				} catch(final IOException ie) {
				}
			}
			switch( decoder.mInputFormat ) {
			case FORMAT_MP3:
				decoder.mSkipStart = ENCDELAY + 528 + 1;
				if( decoder.enc_delay > -1 ) {
					decoder.mSkipStart = decoder.enc_delay + 528 + 1;
				}
				if( decoder.enc_padding > -1 ) {
					decoder.mSkipEnd = decoder.enc_padding - (528 + 1);
				}
				break;
			case FORMAT_MP2:
			case FORMAT_MP1:
				decoder.mSkipStart = 240 + 1;
				break;
			}
			//
			decoder.mSkipStart *= decoder.mNumChannels;
			decoder.mSkipEnd *= decoder.mNumChannels;
			//
			final long n = decoder.mTotalNumSamples;
			if( n != MAX_U_32_NUM ) {
				final long discard = (long)decoder.mSkipStart + (long)decoder.mSkipEnd;
				decoder.mTotalNumSamples = ( n > discard ? n - discard : 0 );
			}
			//
			decoder.printInputFormat();
			//
			int num_channels = decoder.mNumChannels;
			//
			System.out.println("Start decoding " + args[0]);
			out = new RandomAccessFile( args[1], "rw" );
			setSoundFormat( decoder.mSampleRate, 16, num_channels );
			out.write( PCM_HEADER );
			//
			final short[] out_buff = decoder.mOutClipped;
			final byte data[] = new byte[2 * 1152 * 2];
			final byte buf[] = new byte[1024];
			long wavsize = 0;
			int read;
			do {
				int used;
				do {
					num_channels = decoder.mNumChannels;
					final int sample_rate = decoder.mSampleRate;
					//
					int len = 0;
					// read until we get a valid output frame
					while( (read = decoder.decodeHeaders( buf, len, out_buff, OUT_SIZE )) == 0 ) {
						len = rd.read( buf, 0, 1024 );
						if( len <= 0 ) {// java: len = -1 if eof
							len = 0;// java: len = -1 if eof
							// we are done reading the file, but check for buffered data
							read = decoder.decodeHeaders( buf, len, out_buff, OUT_SIZE );
							if( read <= 0 ) {
								read = -1;// done with file
							}
							break;
						}
					}
					// read < 0:  error, probably EOF
					// read = 0:  not possible with lame_decode_fromfile() ???
					// read > 0:  number of output samples
					if( read < 0 ) {
						int i = OUT_SIZE;
						do {
							out_buff[--i] = 0;
						} while( i > 0 );
						read = 0;
					}

					if( num_channels != decoder.mNumChannels ) {
						System.err.println("Error: number of channels has changed - not supported");
						read = -1;
					}
					if( decoder.mSampleRate != sample_rate ) {
						System.err.println("Error: sample frequency has changed - not supported");
						read = -1;
					}
					used = decoder.addPcmBuffer( out_buff, read );
				} while( used <= 0 && read > 0 );
				read = decoder.takePcmBuffer( out_buff, used, OUT_SIZE );
				if( read > 0 ) {
					wavsize += read;
					ByteBuffer.wrap( data, 0, read << 1 ).order( ByteOrder.LITTLE_ENDIAN ).asShortBuffer().put( out_buff, 0, read );
					out.write( data, 0, read << 1 );
				}
			} while( read > 0 );
			//
			if( wavsize <= 0 ) {
				System.err.println("WAVE file contains 0 PCM samples");
			} else if( wavsize > 0xFFFFFFD0L / 2 ) {// 2 bytes per int16
				System.err.println("Very huge WAVE file, can't set filesize accordingly");
				wavsize = 0xFFFFFFD0L;
			} else {
				wavsize <<= 1;// 2 bytes per int16
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
			if( rd != null ) {
				try { rd.close(); } catch( final IOException e ) {}
			}
			decoder.ExitMP3();
		}
		System.exit( 0 );
		return;
	}
}
