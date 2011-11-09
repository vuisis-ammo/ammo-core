package edu.vu.isis.ammo.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.os.Environment;
import android.widget.Toast;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

/**
 *  A set of functional loggers.
 *  These are used to trace functional threads.
 *
 */
public abstract class FLogger {
    
    private static boolean isConfigured = false;
    
    public static Logger getLogger(Context context, String name) {
    	if (! isConfigured) {
    		FLogger.configure(context);
    		FLogger.isConfigured = true;
    	}
    	return LoggerFactory.getLogger(name);
    }
    
    public static void configure(Context context) {
        final InputStream inputStream;
        final File configDir = context.getDir("logger", Context.MODE_WORLD_READABLE);
        final File configFile = new File(configDir, "logger.xml");
        Toast.makeText(context, configFile.toString(), Toast.LENGTH_LONG).show();
        final File globalConfigFile = new File(Environment.getDataDirectory(), "logger.xml");
        
		if (configFile.exists()) {
			try {
				inputStream = new FileInputStream(configFile);
			} catch (FileNotFoundException ex) {
				//logger.error("no policy file {} {}", configFile, ex.getStackTrace());
				return;
			}
		} else if (globalConfigFile.exists()) {
			try {
				inputStream = new FileInputStream(globalConfigFile);
			} catch (FileNotFoundException ex) {
				//logger.error("no policy file {} {}", globalConfigFile, ex.getStackTrace());
				return;
			}
		}
		else  {
        	// Toast.makeText(context, R.string.no_log_config_file, Toast.LENGTH_LONG).show();
        	try {
				final InputStream default_config = context.getResources().openRawResource(R.raw.default_logger);				
				final OutputStream out = new FileOutputStream(configFile);
				final byte[] buf = new byte[1024];
				int len;
				while ((len = default_config.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				default_config.close();
				out.close();
        	} catch (NotFoundException ex) {
				//logger.error("asset not available {}", ex.getMessage());
			} catch (FileNotFoundException ex) {
				//logger.error("file not available {}", ex.getMessage());
			} catch (IOException ex) {
				//logger.error("file not writable {}", ex.getMessage());
			}
        	inputStream = context.getResources().openRawResource(R.raw.default_logger);
        }

        final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        try {
            final JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(lc);
            lc.reset();
            configurator.doConfigure(inputStream);
        } catch (JoranException je) {
            // StatusPrinter will handle this
        }
        StatusPrinter.printInCaseOfErrorsOrWarnings(lc);
    }
    
    
    /**
     * This logger traces retrieval messages.
     * It includes following through to the distributor, into the
     * network service, through the channel to the gateway.
     */
    public static final Logger request = LoggerFactory.getLogger("af-request");

    /**
     * This logger tracks the retrieved content, and its deserialization
     * into a content provider.
     */
    public static final Logger response = LoggerFactory.getLogger("af-response");

    /**
     * When a postal request is made the logger traces its execution.
     * This includes following its progress through the distributor,
     * into the network service, through the channel to the gateway.
     * This working in conjunction with subscribe.
     */
    public static final Logger postal = LoggerFactory.getLogger("af-postal");

    /**
     * This logger traces subscribe messages.
     * It includes following through to the distributor, into the
     * network service, through the channel to the gateway.
     * It also logs the postal content, and its deserialization
     * into a content provider.
     */
    public static final Logger subscribe = LoggerFactory.getLogger("af-subscribe");

    /**
     * This logger traces retrieval messages.
     * It includes following through to the distributor, into the
     * network service, through the channel to the gateway.
     * It also logs the retrieved content, and its deserialization
     * into a content provider.
     */
    public static final Logger retrieval = LoggerFactory.getLogger("af-retrieval");
    

}
