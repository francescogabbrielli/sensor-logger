import httplib
from base64 import b64encode
from threading import Lock,Thread,Event
import re
from time import time


class StreamClient:
    """
    A sample Stream Client to access the SensorLogger app streaming
    """

    CHUNK_SIZE = 32768
    BUFFER_SIZE = 200000

    def __init__(self, host, port):
        self.conn = httplib.HTTPConnection(host, port);
        self.re_length = re.compile(r".*\r\nContent-length: ?([0-9]+).*", re.M | re.S | re.I)
        self.re_type = re.compile(r".*\r\nContent-type: ?([a-z]+)/.*", re.M | re.S | re.I)
        self.buffer = "0"*StreamClient.BUFFER_SIZE
        self.lock = Lock()
        self.last_update = 0
        self.blen = 0
        self.boundary = ""

    def get(self, get="/", user=None, pw=""):
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
        self.boundary = self.boundary[self.boundary.find("boundary=") + 9:]
        self.blen = len(self.boundary)
        Thread(target=self.read, args=(Event(),)).start()

    def read(self, event):
        """
        The thread endless process
        """
        d = self.r.read(StreamClient.CHUNK_SIZE)
        while d:
            self.lock.acquire()
            self.buffer = self.buffer[StreamClient.CHUNK_SIZE:]+d
            self.last_update = time()
            self.lock.release()
            d = self.r.read(StreamClient.CHUNK_SIZE)
            #print len(self.buffer)

    def get_current(self):
        """
        Get the current data (possibly from an external thread)
        :return: an array with [flag_is_frame, the actual data]
        """
        self.lock.acquire()
        end = self.buffer.rfind(self.boundary)
        start = self.buffer.rfind(self.boundary, 0, end)
        while end*start > 1:
            all = self.buffer[start:end]
            lh = all.find("\r\n\r\n")+4
            headers = all[:lh]
            ct = self.re_type.match(headers).group(1)
            if ct == "image" or ct == "text":
                break
            end = start
            start = self.buffer.rfind(self.boundary, 0, end)
        self.lock.release()
        if end*start>1:
            try:
                #print all[:lh]
                l = int(self.re_length.match(headers).group(1))
                #print len(all)-lh, l
                #if b+start+l < StreamClient.BUFFER_SIZE:
                return ct, all[lh:lh+l]
            except Exception as e:
                print e
        return False, None
