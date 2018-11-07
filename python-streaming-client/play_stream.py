from io import BytesIO
import matplotlib.pyplot as plt
import matplotlib.image as img
import matplotlib.figure as f
from stream_client import StreamClient
from time import time, sleep
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg
from Tkinter import *
from threading import Thread, Lock
from stream_data import StreamBuffer

# Create a window with figure (remove this part and the display thread if don't need showing)
# https://stackoverflow.com/questions/34764535/why-cant-matplotlib-plot-in-a-different-thread
window=Tk()
fig = f.Figure(figsize=(8,8))
ax = fig.add_subplot(2,1,1)
ax2 = fig.add_subplot(2,1,2)
canvas = FigureCanvasTkAgg(fig, master=window)
canvas.draw()
canvas.get_tk_widget().pack(side=TOP, fill=BOTH, expand=1)
canvas._tkcanvas.pack(side=TOP, fill=BOTH, expand=1)
imageShown = None
line1 = None
line2 = None
line3 = None
frame = None
ylim_changed = False
xlim_changed = False
max_x = 0
min_x = 0
max_y = 1
min_y = 0
lock = Lock()

DATA_LEN = 100
timestamps = range(0,DATA_LEN)
currentIndex = 0
ax2.set_ylim(-15,15)



def show(buffer):
    """
    Show matplotlib image on global imageShown
    :param data: the jpeg data
    """
    global imageShown, line1, line2, line3
    try :
        im = img.imread(BytesIO(buffer.image), format="jpg")
        imageShown.set_data(im)
        line1.set_ydata(buffer.data[0])
        line2.set_ydata(buffer.data[1])
        line3.set_ydata(buffer.data[2])
        canvas.draw()
        plt.pause(0.01)
    except Exception as e:
        print e


# Create stream client
s = StreamClient("192.168.1.3", 8080)
sb = StreamBuffer(DATA_LEN)


#target of display thread
def display():
    global imageShown, line1, line2, line3
    imageShown = ax.imshow(img.imread("img.jpg", format="jpg"))
    line1, = ax2.plot([0]*DATA_LEN, 'r-')
    line2, = ax2.plot([0]*DATA_LEN, 'b-')
    line3, = ax2.plot([0]*DATA_LEN, 'g-')
    sleep(0.1)
    while True:
        show(sb.getCurrent())
        # do another task with the data
        sb.swap()
        sleep(0.01)


def streaming_callback(timestamp, type, data):
    global currentIndex, frame
    if type=="image":
        print timestamp
        sb.updateImage(data)
    elif type:
        try:
            print timestamp, data.rstrip()
            readings = data.rstrip().split(",")
            timestamps[currentIndex] = int(readings[0])
            sb.updateData(currentIndex, readings[1:4])
            currentIndex += 1
            if currentIndex == DATA_LEN:
                currentIndex = 0
        except Exception as e:
            print (e)
            print ("DATA=[%s]" % data.rstrip())


# start client and show stream
s.get(streaming_callback)

# wait for client to buffer some data
sleep(1)

# start display thread
Thread(name="display", target=display).start()

# wait
window.mainloop()
print 'Done'
