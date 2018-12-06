package it.francescogabbrielli.streaming.server;


import java.util.Locale;

/**
 * Buffer where to write the data to be streamed
 */
class Buffer {

    private static final String PART_FORMAT =
            "Content-type: %s\r\n"
                    + "Content-Length: %d\r\n"
                    + "X-Timestamp: %d\r\n"
                    + "\r\n";

    /** Buffer data. Of fixed length BUFFER_SIZE */
    byte[] data;
    /** Actual length of current data */
    int length;
    /** Timestamp of current data */
    long timestamp;

    String contentType;

    Buffer(int size, String contentType) {
        this.data = new byte[size];
        this.contentType = contentType;
    }

    void reset() {
        length = 0;
    }

    /**
     * Write data into this buffer
     * @param data the actual data to be streamed
     * @param timestamp the timestamp of the data
     */
    void setData(byte[] data, long timestamp) {
        length = data.length;
        System.arraycopy(data, 0, this.data, 0, length);
        this.timestamp = timestamp;
    }

    /**
     * Append data to this buffer
     * @param data
     * @param timestamp
     */
    void appendData(byte[] data, long timestamp) {
        int curr = length;
        length += data.length;
        System.arraycopy(data, 0, this.data, curr, data.length);
        this.timestamp = timestamp;
    }


    String getPartHeaders() {
        return String.format(Locale.US, PART_FORMAT, contentType, length, timestamp);
    }

}