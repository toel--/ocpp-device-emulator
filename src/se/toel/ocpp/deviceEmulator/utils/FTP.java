/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package se.toel.ocpp.deviceEmulator.utils;

import it.sauronsoftware.ftp4j.FTPClient;
import java.io.File;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.toel.util.Dev;
import se.toel.util.StringUtil;

/**
 *
 * @author toel
 */
public class FTP {

    /***************************************************************************
     * Constants and variables
     **************************************************************************/
    private static final Logger logger = LoggerFactory.getLogger(FTP.class);

     /***************************************************************************
     * Constructor
     **************************************************************************/
    private FTP() {}

     /***************************************************************************
     * Public methods
     **************************************************************************/
    public static boolean sendFile(String dst, File file) {
        
        Config conf = new Config();
        boolean ok = true;
        
        // ftp://diag_device_dev_user:password@ftp.oamportal.com/
        
        conf.type = StringUtil.getUntil(dst, "://", false);
        dst = dst.substring(conf.type.length()+3);
        conf.username = StringUtil.getUntil(dst, ":", false);
        dst = dst.substring(conf.username.length()+1);
        conf.password = StringUtil.getUntil(dst, "@", false);
        dst = dst.substring(conf.password.length()+1);
        conf.server = StringUtil.getUntil(dst, "/", false);
        dst = dst.substring(conf.server.length()+1);
        
        
        FTPClient ftp = getFTP(conf);
        ok = ftp!=null;
        if (ok) ok = upload(ftp, file);
        closeFTP(ftp);
        
        return ok;
        
        
    }
    
    public static boolean downloadFile(String src, File file) {
        
        Config conf = new Config();
        boolean ok = true;
        
        // ftp://diag_device_dev_user:password@ftp.oamportal.com/
        
        conf.type = StringUtil.getUntil(src, "://", false);
        src = src.substring(conf.type.length()+3);
        conf.username = StringUtil.getUntil(src, ":", false);
        src = src.substring(conf.username.length()+1);
        conf.password = StringUtil.getUntil(src, "@", false);
        src = src.substring(conf.password.length()+1);
        conf.server = StringUtil.getUntil(src, "/", false);
        src = src.substring(conf.server.length()+1);
        
        
        FTPClient ftp = getFTP(conf);
        ok = ftp!=null;
        if (ok) ok = download(ftp, src, file);
        closeFTP(ftp);
        
        return ok;
        
        
    }
    
    
    /***************************************************************************
     * Private methods
     **************************************************************************/
    
    /** get a ftp object with open communication */
    private static FTPClient getFTP(Config export) {

        FTPClient ftp = new FTPClient();
        boolean success = true;
        
        logger.info("Connecting to FTP Server " + export.server);

        // Check if an alternative port number is part of the server name
        int port = 21;
        
        boolean useSSL = false;
        if (export.type.equalsIgnoreCase("FTPS")) {
            ftp.setSecurity(FTPClient.SECURITY_FTPS); // enables FTPS
            port = 990;
            useSSL = true;
        }
        if (export.type.equalsIgnoreCase("FTPES")) {
            ftp.setSecurity(FTPClient.SECURITY_FTPES); // enables FTPES
            port = 990;
            useSSL = true;
        }
        
        int p = export.server.indexOf(":");
        if (p > 0) {
            port = Integer.valueOf(export.server.substring(p + 1));
            export.server = export.server.substring(0, p);
        }
        
        if (useSSL) {
            TrustManager[] trustManager = new TrustManager[] { new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                @Override
                public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
                }
                @Override
                public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {
                }
            } };
            SSLContext sslContext = null;
            try {
                sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustManager, new SecureRandom());
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                logger.error("While connecting to FTP using SSL " + export.server, e);
            }
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            ftp.setSSLSocketFactory(sslSocketFactory);
        }

        try {
            ftp.setPassive(export.transferMode.equalsIgnoreCase("Passive")); // Passive mode
            ftp.setType(FTPClient.TYPE_BINARY);
            ftp.connect(export.server, port);
            ftp.login(export.username, export.password);
            if (!export.directory.isEmpty()) ftp.changeDirectory(export.directory);

        } catch (Exception e) {
            logger.error("While connecting to FTP {}", export.server, e);
            success = false;
        }

        if (!success) {
            closeFTP(ftp);
            ftp = null;
        }

        return ftp;
    }

    /** close the ftp connection */
    private static void closeFTP(FTPClient ftp) {
        if (ftp != null) {
            try {
                ftp.disconnect(true);      // Cloce gracefully
            } catch (Exception e) {
                // Could get exception here depending on the ftp server type, just force close it
                try {
                    ftp.disconnect(false);   // Close not so gracefully
                } catch (Exception e2) {}
            }
        }
    }
    
    // Upload the file and retry in failure
    private static boolean upload(FTPClient ftp, File file) {
     
        boolean success = false;
        int retries = 1;
        
        while (!success) {
         
            try {
                ftp.upload(file);
                success = true;
            } catch (Exception e) {
                logger.error("While sending file "+file, e);
                retries--;
                if (retries<1) break;
                Dev.sleep(1000);
            }
            
        }
        
        return success;
        
    }
    
    // Upload the file and retry in failure
    private static boolean download(FTPClient ftp, String remoteFile, File file) {
     
        boolean success = false;
        int retries = 1;
        
        while (!success) {
         
            try {
                ftp.download(remoteFile, file);
                success = true;
            } catch (Exception e) {
                logger.error("While downloading file "+file, e);
                retries--;
                if (retries<1) break;
                Dev.sleep(1000);
            }
            
        }
        
        return success;
        
    }
    
    
    private static class Config {
        
        public String type;
        public String server;
        public String transferMode = "Passive";
        public String username;
        public String password;
        public String directory = "";
        
    }

}
