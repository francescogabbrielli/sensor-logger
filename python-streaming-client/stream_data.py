from threading import Lock
from copy import copy


class SensorData:

    def __init__(self, len, dimension, data=None):
        self.dimension = dimension
        self.len = len
        if data is None:
            data = [[0] * len for i in range(0, dimension)]
        self.data = data

    def getdata(self):
        return [data[:] for data in self.data]

    def update(self, index, values):
        for i in range(0, self.dimension):
            self.data[i][index] = float(values[i])

    def __getitem__(self, item):
        return self.data[item]

    def __copy__(self):
        return SensorData(self.len, self.dimension, self.data[:])


class Buffer:

    def __init__(self, len, image=None, data=None):
        self.data = SensorData(len, 3) if data is None else copy(data)
        self.image = image

    def __copy__(self):
        return Buffer(self.data.len, image=self.image, data=self.data)


class StreamBuffer:

    N_BUFFERS = 3

    def __init__(self, len):
        self.buffers = [Buffer(len) for i in range(0, StreamBuffer.N_BUFFERS)]
        self.current = 0
        self.lock = Lock()

    def updateImage(self, image):
        self.lock.acquire()
        buf = self.buffers[self.current]
        buf.image = image
        self.lock.release()

    def updateData(self, index, values):
        self.lock.acquire()
        buf = self.buffers[self.current]
        buf.data.update(index, values)
        self.lock.release()

    def getCurrent(self):
        return self.buffers[self.current]

    def swap(self):
        self.lock.acquire()
        buf = self.buffers[self.current]
        self.current = (self.current + 1) % StreamBuffer.N_BUFFERS
        self.buffers[self.current] = copy(buf)
        self.lock.release()
