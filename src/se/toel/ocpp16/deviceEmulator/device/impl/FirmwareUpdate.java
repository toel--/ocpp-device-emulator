/*
 * Class to emulate a firmware update
 */

package se.toel.ocpp16.deviceEmulator.device.impl;

import java.io.File;
import se.toel.ocpp16.deviceEmulator.utils.FTP;
import se.toel.util.Dev;
import se.toel.util.FileUtils;
import se.toel.util.StringUtil;

/**
 *
 * @author toel
 */
public class FirmwareUpdate {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    // 7.25
    public static final String
        FIRMWARE_STATUS_IDLE = "Idle",                              // Charge Point is not performing firmware update related tasks. Status Idle SHALL only be used as in a FirmwareStatusNotification.req that was triggered by a TriggerMessage.req
        FIRMWARE_STATUS_DOWNLOADING = "Downloading",                // Firmware is being downloaded.
        FIRMWARE_STATUS_DOWNLOADED = "Downloaded",                  // New firmware has been downloaded by Charge Point.
        FIRMWARE_STATUS_DOWNLOAD_FAILED = "DownloadFailed",         // Charge point failed to download firmware.
        FIRMWARE_STATUS_INSTALLING = "Installing",                  // Firmware is being installed.
        FIRMWARE_STATUS_INSTALLED = "Installed",                    // New firmware has successfully been installed in charge point.
        FIRMWARE_STATUS_INSTALLATION_FAILED = "InstallationFailed"; // Installation of new firmware has failed.

    private String location;                           // Required. This contains a string containing a URI pointing to a location from which to retrieve the firmware.
    private int retries;                               // Optional. This specifies how many times Charge Point must try to upload the diagnostics before giving up. If this field is not present, it is left to Charge Point to decide how many times it wants to retry.
    private int retryInterval;                         // Optional. The interval in seconds after which a retry may be attempted. If this field is not present, it is left to Charge Point to decide how long to wait between attempts.
    private String retrieveDate;                       // Required. This contains the date and time after which the Charge Point must retrieve the (new) firmware.
    private String version = "0.0.0";
    private String status = FIRMWARE_STATUS_IDLE;
    private boolean statusHasChanged = true;

     /***************************************************************************
     * Constructor
     **************************************************************************/

     /***************************************************************************
     * Public methods
     **************************************************************************/
    public void downloadFirmware() {
     
        File firmware = new File("firmware.dat");
        firmware.delete();
        
        try {
            FTP.downloadFile(location, firmware);
        } catch (Exception e) {
            Dev.error("While downloading "+location, e);
        }
        
        if (firmware.exists() && firmware.length()>0) {
            setStatus(FIRMWARE_STATUS_DOWNLOADED);
        } else {
            setStatus(FIRMWARE_STATUS_DOWNLOAD_FAILED);
        }
        
        
    }
    
    public void startInstallFirmware() {
     
        File firmware = new File("firmware.dat");
        if (firmware.exists() && firmware.length()>0) {
            setStatus(FIRMWARE_STATUS_INSTALLING);
        } else {
            setStatus(FIRMWARE_STATUS_INSTALLATION_FAILED);
        }
        
    }

    /***************************************************************************
     * Private methods
     **************************************************************************/
    
    /***************************************************************************
     * Getters and setters
     **************************************************************************/
        
        
        
    /**
     * @return the location
     */
    public String getLocation() {
        return location;
    }

    /**
     * @param location the location to set
     */
    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * @return the retries
     */
    public int getRetries() {
        return retries;
    }

    /**
     * @param retries the retries to set
     */
    public void setRetries(int retries) {
        this.retries = retries;
    }

    /**
     * @return the retryInterval
     */
    public int getRetryInterval() {
        return retryInterval;
    }

    /**
     * @param retryInterval the retryInterval to set
     */
    public void setRetryInterval(int retryInterval) {
        this.retryInterval = retryInterval;
    }

    /**
     * @return the retrieveDate
     */
    public String getRetrieveDate() {
        return retrieveDate;
    }

    /**
     * @param retrieveDate the retrieveDate to set
     */
    public void setRetrieveDate(String retrieveDate) {
        this.retrieveDate = retrieveDate;
    }

    /**
     * @return the status
     */
    public String getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(String status) {
        if (!this.status.equals(status)) {
            this.status = status;
            this.statusHasChanged = true;
        }
    }

    /**
     * Reset the status and return the value before reset
     * @return the status
     */
    public boolean getStatusHasChanged() {
        boolean changed = statusHasChanged;
        statusHasChanged = false;
        return changed;
    }
        
    public String getVersion() {
        return FileUtils.getFileNameWithoutExtention(location);
    }
    
    
        
}
