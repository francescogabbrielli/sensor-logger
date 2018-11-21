import httplib
from base64 import b64encode
from threading import Lock,Thread
import re
from time import time, sleep
from copy import copy
from io import BytesIO
import matplotlib.image as img
import matplotlib.figure as fig
import matplotlib.pyplot as plt
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg
from Tkinter import *
import traceback


class StreamClient:
    """
    A sample streaming client for accessing the SensorLogger app
    multipart streaming
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
            #print "---------------------------", len(d)
            #print d
            self.buffer = self.buffer[StreamClient.CHUNK_SIZE:]+d
            self.last_update = time()
            if start<0:
                start = self.buffer.find(self.boundary, restart)
            end = self.buffer.find(self.boundary, start+self.blen)
            while start<end:
                #print (start, end, self.buffer[end+self.blen:end+100])
                all = self.buffer[start+self.blen:end]
                headers_len = all.find("\r\n\r\n") + 4
                headers = all[:headers_len]
                #print headers
                try:
                    content_type = self.re_type.match(headers).group(1)
                    timestamp = self.re_timestamp.match(headers).group(1)
                    #print "FOUND: "+content_type, timestamp
                    l = end - start - headers_len - 4
                    self.callback(timestamp, content_type, all[headers_len:headers_len+l+1])
                except Exception as e:
                    print e
                    traceback.print_exc()
                    #print ("ERROR CONTENT TYPE: %s" % headers)
                timestamp = -1
                #try:
                # l = int(self.re_length.match(headers).group(1))
                #finally:
                # print (all[:min(128,end)])
                # print "--------------------------------------------------"
                start = end
                end = self.buffer.find(self.boundary, start+self.blen)
            d = self.r.read(StreamClient.CHUNK_SIZE)
            start -= StreamClient.CHUNK_SIZE
            restart = max(0, start, restart-StreamClient.CHUNK_SIZE)


class Buffer:

    def __init__(self, image, data):
        self.image = image
        self.data = data

    def __copy__(self):
        return Buffer(self.image, [d[:] for d in self.data])


class StreamBuffer:

    N_BUFFERS = 5

    def __init__(self, len):
        self.buffers = None
        self.currentIndex = 0
        self.current = None
        self.len = len
        self.lock = Lock()
        self.re = re.compile(r"([a-zA-Z _\-]+[^XYZ]) ?([XYZ])?")
        self.init_buffers(0)

    def init_buffers(self, dimension):
        self.lock.acquire()
        data = [[0] * self.len for i in range(0, dimension)]
        self.buffers = [Buffer(None, data) for i in range(0, StreamBuffer.N_BUFFERS)]
        self.lock.release()

    def update_image(self, image):
        self.lock.acquire()
        try:
            buf = self.buffers[self.currentIndex]
            buf.image = image
        finally:
            self.lock.release()

    def update_data(self, i, values):
        self.lock.acquire()
        try:
            buf = self.buffers[self.currentIndex]
            for j,v in enumerate(values):
                buf.data[j][i] = float(v)
        finally:
            self.lock.release()

    def set_headers(self, values):
        old = ""
        dim = 0
        sensors = []

        for v in values:
            m = self.re.match(v)
            name = m.group(1)
            if name != old:
                if old != "":
                    sensors.append({"name": old, "dimension": dim})
                dim = 1
                old = name
            else:
                dim += 1

        sensors.append({"name": name, "dimension": dim})
        self.init_buffers(len(values))
        return sensors

    def swap(self):
        self.lock.acquire()
        current = self.buffers[self.currentIndex]
        self.currentIndex = (self.currentIndex + 1) % StreamBuffer.N_BUFFERS
        self.buffers[self.currentIndex] = copy(current)
        self.lock.release()
        return current


class SensorDisplay:

    def __init__(self, name, dimension, length, axis, labels=("X", "Y", "Z", "par1", "par2"), styles=("r-","g-","b-","y-","m-")):
        self.sensor = name
        self.dimension = dimension
        self.labels = labels
        self.lines = []
        axis.set_ylim(-15, 15)
        axis.set_title(name)
        for i in range(0, dimension):
            line, = axis.plot([0] * length, styles[i])
            if dimension>1:
                line.set_label(labels[i])
            self.lines.append(line)
        axis.legend()

    def set_data(self, data):
        for i,line in enumerate(self.lines):
            line.set_ydata(data[i])


class StreamDisplay:
    """
    Create a window with figure (remove this part and the display thread if don't need showing)
    https://stackoverflow.com/questions/34764535/why-cant-matplotlib-plot-in-a-different-thread
    """

    def __init__(self, buffer):

        self.buffer = buffer
        self.window = Tk()
        self.figure = fig.Figure(figsize=(8, 10))
        self.charts = []

    def draw(self):
        buffer = self.buffer.swap()
        try:
            if buffer.image is not None:
                im = img.imread(BytesIO(buffer.image), format=self.img_format)
                self.image.set_data(im)
            if len(buffer.data):
                curr = 0
                for chart in self.charts:
                    chart.set_data(buffer.data[curr:curr+chart.dimension])
                    curr += chart.dimension
            self.canvas.draw()
            plt.pause(0.01)
        except Exception as e:
            print e
            #traceback.print_stack()

    def wait(self):
        self.window.mainloop()

    def show(self, sensors=[], img_format="jpg"):
        # init axes
        self.sensors = sensors
        self.img_format = img_format
        self.image = self.figure.add_subplot(len(sensors)+1,1,1).imshow(img.imread("img.jpg", format=img_format))
        axes = []
        for i, s in enumerate(sensors):
            ax = self.figure.add_subplot(len(sensors)+1, 1, i+2, label=s["name"])
            self.charts.append(SensorDisplay(s["name"], s["dimension"], self.buffer.len, ax))

        # init canvas
        canvas = FigureCanvasTkAgg(self.figure, master=self.window)
        canvas.draw()
        canvas.get_tk_widget().pack(side=TOP, fill=BOTH, expand=1)
        canvas._tkcanvas.pack(side=TOP, fill=BOTH, expand=1)
        self.canvas = canvas

        # start display thread
        Thread(name="display", target=self.thread, args=(self, 0.01)).start()

    def thread(self, display, delay):
        """ target of display thread """
        while True:
            sleep(delay)
            display.draw()
            # do another task with the data
