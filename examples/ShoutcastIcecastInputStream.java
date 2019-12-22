package examples;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.EventListener;
import javax.swing.event.EventListenerList;

/**
 * Buffered SHOUTcast-Icecast audio stream.
 */
final class ShoutcastIcecastInputStream extends InputStream {
	/**
	 * The metadata listener interface for receiving medata information.
	 */
	interface MetadataListener extends EventListener {
		/**
		 * Invoked to publish new metadata.
		 * @param metadata text to publish.
		 */
		void publish(String metadata);
	}
	/** Connection timeout */
	private static final int CONNECTION_TIMEOUT_MS = 30000;
	/** Buffer size. The size must be enough to recognize a stream encoding. */
	private static final int BUFFER_SIZE = 1 << 19;
	//
	/** HTTP protocol connection. */
	private final HttpURLConnection mConnection;
	/** Buffered stream to read data from the connection. */
	private final BufferedInputStream mInputStream;
	/** A period in bytes for sending metadata information. */
	private final int mMetadataPeriod;
	/** How many bytes is read. Used if metadata period not zero. */
	private int mReadPosition = 0;
	/** Array to store metadata. */
	private final byte[] mMetaData;
	/** A list of event listeners for this component. */
	private final EventListenerList mEventListeners = new EventListenerList();
	//
	/**
	 * Creates buffered stream with the specified buffer size and the metadata period to read SHOUTcast/Icecast data.
	 *
	 * @param url http address.
	 * @param isReadMetadata true - asks for sending metadata, false - metadata do not needed.
	 * @param size desired buffer size. Recommended size is 2^19.
	 * @throws IllegalArgumentException - if size <= 0 or metadataPeriod < 0.
	 */
	public ShoutcastIcecastInputStream(final String url, final boolean isReadMetadata, final int size) throws IOException {
		if( ! url.toLowerCase().startsWith("http://") ) {
			throw new IllegalArgumentException("Error: not http url");
		}
		mConnection = (HttpURLConnection)new URL( url ).openConnection();
		mConnection.setRequestMethod("GET");
		//mConnection.setRequestProperty("User-Agent", APP_NAME );
		mConnection.setRequestProperty("Accept", "*/*");
		mConnection.setRequestProperty("Icy-MetaData", isReadMetadata ? "1" : "0");
		mConnection.setRequestProperty("Connection", "keep-alive");
		mConnection.setConnectTimeout( CONNECTION_TIMEOUT_MS );
		mConnection.setReadTimeout( CONNECTION_TIMEOUT_MS );
		mConnection.setUseCaches( false );
		mConnection.connect();
		final int ret = mConnection.getResponseCode();
		if( ret != HttpURLConnection.HTTP_OK ) {
			throw new IOException("Error: response code is " + ret );
		}
		/* print response
		System.out.println("---- Server response:");
		final Set<String> keys = mConnection.getHeaderFields().keySet();
		for( final Iterator<String> it = keys.iterator(); it.hasNext(); ) {
			final String key = it.next();
			if( key != null ) {
				System.out.println( key + " " + mConnection.getHeaderField( key ) );
			}
		}
		System.out.println("----");
		*/
		if( ! mConnection.getHeaderField("Content-Type").startsWith("audio/") &&
			! mConnection.getHeaderField("Content-Type").startsWith("application/ogg") ) {
			throw new IOException("Error: not a SHOUTcast / Icecast stream");
		}// icecast/shoutcast
		// System.out.println("SHOUTcast / Icecast protocol is detected");
		final int metadata_period = mConnection.getHeaderFieldInt( "icy-metaint", 0 );
		if( metadata_period < 0 ) {
			throw new IOException("Error: metadata period = " + metadata_period );
		}
		mMetadataPeriod = metadata_period;
		byte[] b = null;
		if( metadata_period != 0 ) {
			b = new byte[255 * 16];
		}
		mMetaData = b;
		mInputStream = new BufferedInputStream( mConnection.getInputStream(), size >= BUFFER_SIZE ? size : BUFFER_SIZE );
	}

	/**
	 * Adds an <code>MetadataListener</code> to the stream.
	 * @param l the <code>MetadataListener</code> to be added
	 */
	final ShoutcastIcecastInputStream addMetadataListener(final MetadataListener l) {
		mEventListeners.add( MetadataListener.class, l );
		return this;
	}

	/**
	 * Removes an <code>MetadataListener</code> from the stream.
	 *
	 * @param l the listener to be removed
	 */
	final void removeMetadataListener(final MetadataListener l) {
		mEventListeners.remove( MetadataListener.class, l );
	}

	/**
	 * Notifies all listeners that have registered interest for
	 * notification on this event type.
	 */
	private final void notifyMetadataListeners(final int length) {
		String str;
		try {
			str = new String( mMetaData, 0, length, "UTF-8");
		} catch(final Exception e) {
			str = new String( mMetaData, 0, length );
		}
		// System.out.println( str );
		int i = str.indexOf("StreamTitle=");
		if( i >= 0 ) {
			i += "StreamTitle=".length();
			char end = str.charAt( i++ );
			if( end != '\'' && end != '"' ) {
				end = ';';
				i--;
			}
			final int j = str.indexOf( end, i );
			str = str.substring( i, j < 0 ? str.length() : j );
			// Guaranteed to return a non-null array
			final Object[] listeners = mEventListeners.getListenerList();
			// Process the listeners last to first, notifying
			// those that are interested in this event
			for( i = listeners.length - 2; i >= 0; i -= 2 ) {
				if( listeners[i] == MetadataListener.class ) {
					((MetadataListener)listeners[i + 1]).publish( str );
				}
			}
		}
	}

	@Override
	public final void close() throws IOException {
		mConnection.disconnect();
		mInputStream.close();
	}

	@Override
	public final boolean markSupported() {
		return true;
	}

	@Override
	public synchronized final void mark(final int readlimit) {
		mInputStream.mark( readlimit );
	}
	@Override
	public synchronized final void reset() throws IOException {
		mInputStream.reset();
		mReadPosition = 0;
	}

	@Override
	public final int available() throws IOException {
		return mInputStream.available();
	}

	@Override
	public synchronized final int read() throws IOException {
		if( mMetadataPeriod == 0 ) {
			return mInputStream.read();
		}
		if( mReadPosition >= mMetadataPeriod ) {
			// Get metadata length, max size is 255 * 16 = 4080 bytes without zero
			int length = mInputStream.read();
			if( length < 0 ) {
				return length;
			}
			length <<= 4;// size of the metadata
			if( length > 0 ) {
				int off = 0;
				int read;
				while( (read = mInputStream.read( mMetaData, off, length - off )) > 0 ) {
					off += read;
				}
				notifyMetadataListeners( length );
			}
			mReadPosition = 0;
		}
		mReadPosition++;
		return mInputStream.read();
	}
	@Override
	public synchronized final int read(final byte[] b, int off, int len) throws IOException {
		if( mMetadataPeriod == 0 ) {
			return mInputStream.read( b, off, len );
		}
		int data = mMetadataPeriod - mReadPosition;
		if( data < len ) {
			int read = mInputStream.read( b, off, data );
			if( read < data ) {
				mReadPosition += read > 0 ? read : 0;
				return read;
			}
			off += data;
			len -= data;
			// Get metadata length, max size is 255 * 16 = 4080 bytes without zero
			int length = mInputStream.read();
			if( length < 0 ) {
				return length;
			}
			length <<= 4;// size of the metadata
			if( length > 0 ) {
				int offset = 0;
				int r;
				while( (r = mInputStream.read( mMetaData, offset, length - offset )) > 0 ) {
					offset += r;
				}
				notifyMetadataListeners( length );
			}
			data = mMetadataPeriod;
			while( data < len ) {
				int r = mInputStream.read( b, off, data );
				if( r < data ) {
					if( r < 0 ) {
						r = 0;// buffer has some data. eof will be returns while next read
					}
					read += r;
					mReadPosition = r;
					return read;
				}
				read += r;
				off += data;
				len -= data;
				// Get metadata length, max size is 255 * 16 = 4080 bytes without zero
				length = mInputStream.read();
				if( length < 0 ) {
					return length;
				}
				length <<= 4;// size of the metadata
				if( length > 0 ) {
					int offset = 0;
					while( (r = mInputStream.read( mMetaData, offset, length - offset )) > 0 ) {
						offset += r;
					}
					notifyMetadataListeners( length );
				}
			}
			int r = mInputStream.read( b, off, len );
			if( r < 0 ) {
				r = 0;// buffer has some data. eof will be returns while next read
			}
			read += r;
			mReadPosition = r;
			return read;
		}
		final int read = mInputStream.read( b, off, len );
		mReadPosition += read > 0 ? read : 0;
		return read;
	}
}
