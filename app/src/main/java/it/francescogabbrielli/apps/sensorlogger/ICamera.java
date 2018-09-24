package it.francescogabbrielli.apps.sensorlogger;

import android.hardware.Camera;

public interface ICamera {

    void takePicture(Camera.PictureCallback pictureCallback);

    void pictureTaken();

}
