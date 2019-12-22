package spi.convert;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.spi.FormatConversionProvider;

import libmp3lame.Jutil;

// TODO what must return getSourceEncodings(), getTargetEncodings ?
public class Mp3_FormatConversionProvider extends FormatConversionProvider {
	public static final AudioFormat.Encoding ENCODING = new AudioFormat.Encoding("MPEG");

	@Override
	public Encoding[] getSourceEncodings() {
		System.err.println("Mp3_FormatConversionProvider.getSourceEncodings");
		return null;
	}

	@Override
	public Encoding[] getTargetEncodings() {
		System.err.println("Mp3_FormatConversionProvider.getTargetEncodings");
		return null;
	}

	@Override
	public Encoding[] getTargetEncodings(final AudioFormat sourceFormat) {
		final int channels = sourceFormat.getChannels();
		final int index = Jutil.SmpFrqIndex( (int)sourceFormat.getSampleRate() );
		if( index >= 0 &&
			sourceFormat.getEncoding() == Encoding.PCM_SIGNED &&
			sourceFormat.getSampleSizeInBits() == 16 &&
			(channels == 1 || channels == 2) )
		{
			final Encoding enc[] = new Encoding[] { ENCODING };
			return enc;
		}
		return new Encoding[0];
	}

	@Override
	public AudioFormat[] getTargetFormats(final Encoding targetEncoding, final AudioFormat sourceFormat) {
		final int channels = sourceFormat.getChannels();
		if( sourceFormat.getEncoding().equals( ENCODING ) && (channels == 2 || channels == 1) ) {
			if( targetEncoding == Encoding.PCM_SIGNED ) {
				final AudioFormat af[] = {
					new AudioFormat( sourceFormat.getSampleRate(), 16, sourceFormat.getChannels(), true, false ),
					new AudioFormat( sourceFormat.getSampleRate(), 16, sourceFormat.getChannels(), true, true ),
					new AudioFormat( sourceFormat.getSampleRate(), 24, sourceFormat.getChannels(), true, false ),
					new AudioFormat( sourceFormat.getSampleRate(), 24, sourceFormat.getChannels(), true, true ),
					new AudioFormat( sourceFormat.getSampleRate(), 8, sourceFormat.getChannels(), true, false ),
					new AudioFormat( sourceFormat.getSampleRate(), 8, sourceFormat.getChannels(), true, true ),
				};
				return af;
			}
			if( targetEncoding == Encoding.PCM_UNSIGNED ) {
				final AudioFormat af[] = {
					new AudioFormat( sourceFormat.getSampleRate(), 16, sourceFormat.getChannels(), false, false ),
					new AudioFormat( sourceFormat.getSampleRate(), 16, sourceFormat.getChannels(), false, true ),
					new AudioFormat( sourceFormat.getSampleRate(), 24, sourceFormat.getChannels(), false, false ),
					new AudioFormat( sourceFormat.getSampleRate(), 24, sourceFormat.getChannels(), false, true ),
					new AudioFormat( sourceFormat.getSampleRate(), 8, sourceFormat.getChannels(), false, false ),
					new AudioFormat( sourceFormat.getSampleRate(), 8, sourceFormat.getChannels(), false, true ),
				};
				return af;
			}
		}
		return new AudioFormat[0];
	}

	@Override
	public AudioInputStream getAudioInputStream(final Encoding targetEncoding,
					final AudioInputStream sourceStream) {

		final AudioFormat saf = sourceStream.getFormat();
		final AudioFormat taf = new AudioFormat( targetEncoding,
						saf.getSampleRate(), 16, saf.getChannels(),
						AudioSystem.NOT_SPECIFIED, -1.0f, saf.isBigEndian() );

		return getAudioInputStream( taf, sourceStream );
	}

	@Override
	public AudioInputStream getAudioInputStream(final AudioFormat targetFormat,
					final AudioInputStream sourceStream) {

		return new Mp3_DecodedAudioInputStream( sourceStream, targetFormat, AudioSystem.NOT_SPECIFIED );
	}
}
