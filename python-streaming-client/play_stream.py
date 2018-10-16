from io import BytesIO
import matplotlib.pyplot as plt
import matplotlib.image as img
import matplotlib.figure as f
from stream_client import StreamClient
from time import time, sleep
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg
from Tkinter import *
from threading import Thread
import numpy as np
import cv2


# Create a window with figure (remove this part and the display thread if don't need showing)
window=Tk()
fig = f.Figure()
ax = fig.add_subplot(1, 1, 1)
canvas = FigureCanvasTkAgg(fig, master=window)
canvas.draw()
canvas.get_tk_widget().pack(side=TOP, fill=BOTH, expand=1)
canvas._tkcanvas.pack(side=TOP, fill=BOTH, expand=1)
imageShown = None


def show(data):
    """
    Show matplotlib image on global imageShown
    :param data: the jpeg data
    """
    global imageShown
    im = img.imread(BytesIO(data), format="jpg")
    if imageShown is None:
        imageShown = ax.imshow(im)
    else:
        imageShown.set_data(im)
    canvas.draw()
    plt.pause(0.001)


# Create a stream client
s = StreamClient("192.168.1.1", "8080")


#target of display thread
def display():
    type = ""
    while time() - s.last_update < 1:
        [type, frame] = s.get_current()
        if type=="image":
            show(frame)
        elif type:
            print frame
        sleep(0.066)


#target of CV thread
def cv():
    while time()-s.last_update < 1:
        [type,frame] = s.get_current()
        if type=="image":
            # convert data to CV2
            nparr = np.fromstring(frame, np.uint8)
            image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            #do_cv_stuff_here(image, nparr)
            #no need to sleep


# start client and show stream
s.get()

# wait for client to buffer some data
sleep(1)

# start display thread
Thread(name="display", target=display).start()

# start a generic computer vision thread (implement yours)
Thread(name="cv", target=display).start()


# wait
window.mainloop()
print 'Done'
