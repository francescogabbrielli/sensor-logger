# ########################################################################
#
#      An example script for capturing stream and passing it with opencv
#
##########################################################################

import cv2
import msvcrt as m
import numpy as np
from time import sleep
from threading import Thread
from stream_client import StreamClient, StreamBuffer


DATA_LEN = 100


def streaming_callback(timestamp, type, data):
    global shown
    if type=="image":
        #print timestamp
        sb.update_image(timestamp, data)


# Create stream client
sc = StreamClient("192.168.1.2", 8080)#connect to your camera!
# Stream Buffer
sb = StreamBuffer(DATA_LEN)# DATA_LEN is not used here because it is an images only stream


# your thread!
def my_thread():
    buffer = sb.swap()
    nparr = np.fromstring(buffer.image, np.uint8)
    image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    #
    # do your coding here!
    #
    sleep(0.1)#//kind of 10 frames per second :)


# start the client:
#   - you may need to put a relative url in the get
#   - you may need to provide user and password if basic authentication is needed
sc.get(streaming_callback, get="camera_url, generally just empty", user="user", pw="password")

# allow to buffer some frames
sleep(1)

# start a generic computer vision thread (implement yours)
Thread(name="cv", target=my_thread).start()

# wait
m.getch()