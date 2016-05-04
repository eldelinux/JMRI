// SprogVersionQuery.java
package jmri.jmrix.sprog.update;

import java.util.Vector;
import jmri.jmrix.sprog.SprogListener;
import jmri.jmrix.sprog.SprogMessage;
import jmri.jmrix.sprog.SprogReply;
import jmri.jmrix.sprog.SprogTrafficController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Get the firmware version of the attached SPROG
 *
 * Updated April 2016 by Andrew Crosland look for the correct replies, which may 
 * not be the very next message after a query is sent, due to slot manager
 * traffic. Add Pi-SPROG version decoding.
 *
 * @author	Andrew Crosland Copyright (C) 2012, 2016
 * @version	$Revision: $
 */
public class SprogVersionQuery implements SprogListener {

    String replyString;
    static SprogTrafficController tc;
    static SprogVersion ver;

    // enum for version query states
    enum QueryState {

        IDLE,
        CRSENT, // awaiting reply to " "
        QUERYSENT, // awaiting reply to "?"
        DONE
    }       // Version has been found
    static QueryState state = QueryState.IDLE;

    static final protected int LONG_TIMEOUT = 2000;
    static javax.swing.Timer timer = null;

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    // Ignore FindBugs warnings as there can only be one instance at present
    public SprogVersionQuery() {
        if (log.isDebugEnabled()) {
            log.debug("setting instance: " + this);
        }
        self = this;
        tc = SprogTrafficController.instance();
        state = QueryState.IDLE;
    }

    protected static final Vector<SprogVersionListener> versionListeners = new Vector<SprogVersionListener>();

    protected synchronized void addSprogVersionListener(SprogVersionListener l) {
        // add only if not already registered
        if (l == null) {
            throw new java.lang.NullPointerException();
        }
        if (!versionListeners.contains(l)) {
            versionListeners.addElement(l);
        }
    }

    public synchronized void removeSprogVersionListener(SprogVersionListener l) {
        if (versionListeners.contains(l)) {
            versionListeners.removeElement(l);
        }
    }

    @SuppressWarnings("unchecked")
    private static synchronized Vector<SprogVersionListener> getCopyOfListeners() {
        return (Vector<SprogVersionListener>) versionListeners.clone();
    }

    /**
     * static function returning the SprogVersionQuery instance to use.
     *
     * @return The registered SprogVersionQuery instance for general use, if
     *         need be creating one.
     */
    static public SprogVersionQuery instance() {
        if (self == null) {
            if (log.isDebugEnabled()) {
                log.debug("creating a new SprogVersionQuery object");
            }
            self = new SprogVersionQuery();
        }
        return self;
    }
    static volatile protected SprogVersionQuery self = null;

    static synchronized public void requestVersion(SprogVersionListener l) {
        SprogMessage m;
        if (log.isDebugEnabled()) {
            log.debug("SprogVersion requested by " + l.toString());
        }
        if (state == QueryState.DONE) {
            // Reply immediately
            l.notifyVersion(ver);
            return;
        }
        // Remember this listener
        SprogVersionQuery.instance().addSprogVersionListener(l);
        if (state == QueryState.IDLE) {
            // Kick things off with a blank message
            m = new SprogMessage(1);
            m.setOpCode(' ');
            tc.sendSprogMessage(m, SprogVersionQuery.instance());
            state = QueryState.CRSENT;
            startLongTimer();
        }
    }

    /**
     * Notify all registered listeners of the SPROG version
     *
     * @param v
     */
    protected static synchronized void notifyVersion(SprogVersion v) {
        ver = v;
        for (SprogVersionListener listener : getCopyOfListeners()) {
            try {
                listener.notifyVersion(ver);
                versionListeners.remove(listener);
            } catch (Exception e) {
                log.warn("notify: During dispatch to " + listener + "\nException " + e);
            }
        }

    }

    /**
     * SprogListener notify Message not used
     *
     * @param m
     */
    @Override
    public void notifyMessage(SprogMessage m) {
    }   // Ignore

    /**
     * SprogListener notify Reply listens to replies and looks for version reply
     *
     * @param m
     */
    @Override
    synchronized public void notifyReply(SprogReply m) {
        SprogMessage msg;
        SprogVersion v;
        replyString = m.toString();
        switch (state) {
            case IDLE: {
                if (log.isDebugEnabled()) {
                    log.debug("reply in IDLE state");
                }
                break;
            }

            case CRSENT: {
                log.debug("reply in CRSENT state" + replyString);
                if ((replyString.indexOf("P>")) >= 0) {
                    stopTimer();
                    msg = new SprogMessage(1);
                    msg.setOpCode('?');
                    tc.sendSprogMessage(msg, this);
                    state = QueryState.QUERYSENT;
                    startLongTimer();
                }
                break;
            }

            case QUERYSENT: {
                log.debug("reply in QUERYSENT state" + replyString);
                if (replyString.contains("SPROG")) {
                    stopTimer();
                    String[] splits = replyString.split("\n");
                    splits = splits[1].split(" ");
                    int index = 1;
                    log.debug("Elements in version reply: " + splits.length);
                    log.debug("First element: <" + splits[0] + ">");
                    if (splits[0].contains("Pi-SPROG")) {
                        log.debug("Found a Pi-SPROG " + splits[index]);
                        switch (splits[1]) {
                            case "Nano":
                                v = new SprogVersion(new SprogType(SprogType.PISPROGNANO), splits[2].substring(1));
                                break;
                            case "One":
                                v = new SprogVersion(new SprogType(SprogType.PISPROGONE), splits[2].substring(1));
                                break;
                            default:
                                if (log.isDebugEnabled()) {
                                    log.debug("Unrecognised Pi-SPROG " + splits[1]);
                                }
                                v = new SprogVersion(new SprogType(SprogType.NOT_RECOGNISED));
                                break;
                        }                
                    } else if (splits[0].contains("SPROG")) {
                        log.debug("Found a SPROG " + splits[index]);
                        switch (splits[index]) {
                            case "3":
                                index += 2;
                                v = new SprogVersion(new SprogType(SprogType.SPROG3), splits[index]);
                                break;
                            case "IV":
                                index += 2;
                                v = new SprogVersion(new SprogType(SprogType.SPROGIV), splits[index]);
                                break;
                            case "5":
                                index += 2;
                                v = new SprogVersion(new SprogType(SprogType.SPROG5), splits[index]);
                                break;
                            case "Nano":
                                index += 2;
                                v = new SprogVersion(new SprogType(SprogType.NANO), splits[index]);
                                break;
                            case "Sniffer":
                                index += 2;
                                v = new SprogVersion(new SprogType(SprogType.SNIFFER), splits[index]);
                                break;
                            case "II":
                                index++;
                                if (splits[index].equals("USB")) {
                                    index += 2;
                                    v = new SprogVersion(new SprogType(SprogType.SPROGIIUSB), splits[index]);
                                } else {
                                    index++;
                                    v = new SprogVersion(new SprogType(SprogType.SPROGII), splits[index]);
                                }   break;
                            case "Ver":
                                index += 1;
                                v = new SprogVersion(new SprogType(SprogType.SPROGV4), splits[index]);
                                break;
                            default:
                                if (log.isDebugEnabled()) {
                                    log.debug("Unrecognised SPROG" + splits[index]);
                                }
                                v = new SprogVersion(new SprogType(SprogType.NOT_RECOGNISED));
                                break;
                        }
                    } else {
                        // Reply contained "SPROG" but couldn't be parsed
                        log.debug("Found an unknown SPROG " + splits[index]);
                        v = new SprogVersion(new SprogType(SprogType.NOT_RECOGNISED));
                        break;
                    }

                    if ((v.sprogType.sprogType == SprogType.SPROGII) && (v.getMajorVersion() >= 3)) {
                        // Correct for SPROG IIv3 which is different hardware
                        v = new SprogVersion(new SprogType(SprogType.SPROGIIv3), v.sprogVersion);
                    }
                    log.debug("Found: " + v.toString());
                    notifyVersion(v);
                    state = QueryState.DONE;
                    break;
                }
                break;
            }

            case DONE:
                break;

            default: {
                log.error("Unknown case");
            }
        }
    }

    /**
     * Internal routine to handle a timeout
     */
    synchronized static protected void timeout() {
        SprogVersion v;
        switch (state) {
            case CRSENT:
                log.debug("Timeout no SPROG prompt");
                state = QueryState.IDLE;
                v = new SprogVersion(new SprogType(SprogType.TIMEOUT));
                notifyVersion(v);
                break;
            case QUERYSENT:
                log.debug("Timeout no SPROG found");
                state = QueryState.IDLE;
                v = new SprogVersion(new SprogType(SprogType.NOT_A_SPROG));
                notifyVersion(v);
                break;
            case DONE:
            case IDLE:
                log.error("Timeout in unexpected state: " + state);
                break;
        }
    }

    /**
     * Internal routine to restart timer with a long delay
     */
    static protected void startLongTimer() {
        restartTimer(LONG_TIMEOUT);
    }

    /**
     * Internal routine to stop timer, as all is well
     */
    protected void stopTimer() {
        if (timer != null) {
            timer.stop();
        }
    }

    /**
     * Internal routine to handle timer starts & restarts
    * 
     * @param delay timer delay
     */
    static protected void restartTimer(int delay) {
        if (timer == null) {
            timer = new javax.swing.Timer(delay, new java.awt.event.ActionListener() {

                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    timeout();
                }
            });
        }
        timer.stop();
        timer.setInitialDelay(delay);
        timer.setRepeats(false);
        timer.start();
    }

    private final static Logger log = LoggerFactory.getLogger(SprogVersionQuery.class.getName());
}
