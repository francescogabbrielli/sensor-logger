# SensorLogger
A simple version of the "real time" dataset generator using device sensors and camera

## Purpose
This Android app is stil a prototype, mainly for educational purposes. 
The aims of the project are:
- to mantain as far as possible the corrispondence between frames and sensor data
- configurable to be lightweight and not memory/battery consuming
- as fast as possible!
This simplified version uses two different streaming servers to achieve optimal performance.
A simple html client is provided as well

## Features
- Multithread stream both images and sensors to a remote client, using 2 different ports
- Rotate sensor axes

## Usage
Just start the app and connect with the html client provided or whatever client.

## Build
This simplified version builds only in native mode. 
Remember to put inside `jniLibs.srcDirs` the OpenCV static libraries (not included here) of your choice.

## Html client
A very basic [html client](https://github.com/francescogabbrielli/sensor-logger/blob/simple/streaming-client/html/client.html) 
is provided to capture the streaming of both sensors and images

## Licenses
- [OpenCV](https://opencv.org/) - BSD
