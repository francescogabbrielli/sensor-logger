import httplib
from base64 import b64encode
from threading import Lock,Thread
import re
from time import time


class StreamClient:
    """
    A sample Stream Client to access the SensorLogger app streaming
    """

    CHUNK_SIZE = 32768
    BUFFER_SIZE = 200000

    def __init__(self, host, port=80):
        self.conn = httplib.HTTPConnection(host, port);
        self.re_length = re.compile(r".*Content-length: ?([0-9]+).*", re.M | re.S | re.I)
        self.re_type = re.compile(r"Content-type: ?([a-z]+)/.*", re.M | re.S | re.I)
        self.re_timestamp = re.compile(r".*X-Timestamp: ?([0-9]+).*", re.M | re.S | re.I)
        self.buffer = "0"*StreamClient.BUFFER_SIZE
        self.lock = Lock()
        self.last_update = 0
        self.blen = 0
        self.boundary = ""
        self.callback = None

    def get(self, callback, get="/", user=None, pw=""):
        """
        Start the get request of the stream
        :param get: the url to get
        :param user: the user (for basic authentication)
        :param pw: the password
        """
        if user is not None:
            userAndPass = b64encode(b"%s:%s"%(user,pw)).decode("ascii")
            headers = {'Authorization': 'Basic %s' % userAndPass}
            self.conn.request("GET", get, headers=headers)
        else:
            self.conn.request("GET", get)

        self.r = self.conn.getresponse()
        self.boundary = self.r.getheader("content-type")
        self.boundary = "--" + self.boundary[self.boundary.find("boundary=") + 9:]
        self.blen = len(self.boundary)+2
        self.callback = callback
        Thread(target=self.read).start()

    def read(self):
        """
        The reading thread
        """
        print ("START")
        start = -1
        restart = StreamClient.BUFFER_SIZE-StreamClient.CHUNK_SIZE
        d = self.r.read(StreamClient.CHUNK_SIZE)
        while d:
            self.buffer = self.buffer[StreamClient.CHUNK_SIZE:]+d
            self.last_update = time()
            if start<0:
                start = self.buffer.find(self.boundary, restart)
            end = self.buffer.find(self.boundary, start+self.blen)
            while start<end:
                all = self.buffer[start+self.blen:end]
                headers_len = all.find("\r\n\r\n") + 4
                headers = all[:headers_len]
                try:
                    content_type = self.re_type.match(headers).group(1)
                    timestamp = self.re_timestamp.match(headers).group(1)
                    l = end - start - headers_len - 4
                    self.callback(timestamp, content_type, all[headers_len:headers_len+l+1])
                except Exception as e:
                    print e
                    print ("ERROR CONTENT TYPE: %d" % headers_len)
                start = end
                end = self.buffer.find(self.boundary, start+self.blen)
            d = self.r.read(StreamClient.CHUNK_SIZE)
            start -= StreamClient.CHUNK_SIZE
            restart = max(0, start, restart-StreamClient.CHUNK_SIZE)
