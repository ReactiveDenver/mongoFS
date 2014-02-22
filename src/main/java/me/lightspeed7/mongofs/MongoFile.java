package me.lightspeed7.mongofs;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import me.lightspeed7.mongofs.util.FileUtil;
import sun.net.www.protocol.mongofile.Handler;
import sun.net.www.protocol.mongofile.Parser;

/**
 * 
 * mongoFile:fileName.pdf?id#application/pdf
 * 
 * and is mapped to the following URL fields
 * 
 * protocol:path?query#ref
 * 
 * @author David Buschman
 * 
 */
public class MongoFile {

    public static final String PROTOCOL = "mongofile";
    public static final String GZ = "gz";

    private URL url;

    // factories and helpers
    public static final MongoFile construct(String id, String fileName, String mediaType)
            throws MalformedURLException {

        return construct(Parser.construct(id, fileName, mediaType));
    }

    public static final MongoFile construct(String spec)
            throws MalformedURLException {

        return construct(Parser.construct(spec));
    }

    /**
     * Construct a MogoFile object from the given URL, it will be tested from validity
     * 
     * @param url
     * @return a MongoFile object for this URL
     */
    public static final MongoFile construct(URL url) {

        if (url == null) {
            throw new IllegalArgumentException("url cannot be null");
        }

        if (!url.getProtocol().equals(PROTOCOL)) {
            throw new IllegalStateException(String.format("Only %s protocal is valid to be wrapped", PROTOCOL));
        }
        return new MongoFile(url);
    }

    /**
     * Is the given spec a valid MongoFile URL
     * 
     * @param spec
     * @return
     */
    public static final boolean isValidUrl(String spec) {

        try {
            return (null != construct(spec));
        } catch (Throwable t) {
            return false;
        }

    }

    // CTOR - not visible, use construct methods above
    /* package */MongoFile(String spec)
            throws MalformedURLException {

        this.url = new URL(null, spec, new Handler());
    }

    // CTOR- not visible, use construct methods above
    /* package */MongoFile(URL url) {

        this.url = url;
    }

    // toString
    @Override
    public String toString() {

        return this.url.toString();
    }

    // getters

    /**
     * Returns the 'attachment' protocol string
     * 
     * @return the protocol
     */
    public String getProtocol() {

        return url.getProtocol();
    }

    /**
     * Returns the full URL object
     * 
     * @return the URL object
     */
    public URL getUrl() {

        return this.url;
    }

    /**
     * Returns the lookup(Storage) Id from the URL
     * 
     * @return the primary key to the mongoFS system
     */
    public String getAttachmentId() {

        return url.getQuery();
    }

    /**
     * Returns the full path specified in the URL
     * 
     * @return the full file path
     */
    public String getFilePath() {

        return url.getPath();
    }

    /**
     * Return just the last segment in the file path
     * 
     * @return just the filename
     */
    public String getFileName() {

        return new File(url.getPath()).getName();
    }

    /**
     * Returns the extension on the filename
     * 
     * @return the extension on the filename
     */
    public String getExtension() {

        String temp = FileUtil.getExtension(new File(url.getPath()));
        if (temp == null) {
            return null;
        }
        return temp.toLowerCase();
    }

    /**
     * Returns the mime type specified on the URL
     * 
     * @return the media type for the file
     */
    public String getMediaType() {

        return url.getRef();
    }

    /**
     * Is the data stored in the file compressed in the datastore
     * 
     * @return true if compressed, false otherwise
     */
    public boolean isStoredCompressed() {

        if (url.getHost() != null && url.getHost().equals(GZ))
            return true;

        return false;
    }

    /**
     * Is the data compressible based on the media type of the file. This may differ from what is stored in the datasstore
     * 
     * @return
     */
    public boolean isDataCompressed() {

        return !CompressionMediaTypes.isCompressable(getMediaType());
    }

    public boolean isSupportedProtocol(String protocol) {

        if (url.getProtocol().equals(PROTOCOL))
            return true;

        // unknown
        return false;
    }
}