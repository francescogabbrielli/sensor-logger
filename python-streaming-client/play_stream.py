# ########################################################################
#
#      An example script for displaying image and sensors together
#
##########################################################################

from stream_client import StreamClient, StreamBuffer, StreamDisplay

DATA_LEN = 100
currentIndex = 0

# Create stream client
sc = StreamClient("192.168.1.2", 8080)
# Stream Buffer of DATA_LEN readings
sb = StreamBuffer(DATA_LEN)
# Display data/image
sd = StreamDisplay(sb)

shown = False


def update_data(timestamp, lines):
    global shown, currentIndex
    for line in lines:
        if len(line)<2:
            break
        values = line.split(",")[1:]
        #print line
        if ord(values[0][0]) > 64:
            sensors = sb.set_headers(values)
            sd.show(sensors)
            shown = True
            continue
        sb.update_data(timestamp, currentIndex, values)
        currentIndex += 1
        if currentIndex == DATA_LEN:
            currentIndex = 0
        return currentIndex


def streaming_callback(timestamp, type, data):
    global shown
    if type=="image":
        #print timestamp
        sb.update_image(timestamp, data)
        if not shown:
            print "SHOW IMAGES ONLY"
            sd.show()
            shown = True
    elif type:
        data = data.rstrip()
        try:
            #print timestamp, data
            update_data(timestamp, data.split("\n"))
        except Exception as e:
            print (e)
            print ("DATA=[%s]" % data)


# start client and show stream
sc.get(streaming_callback)


# wait
sd.wait()
print 'Done'
