package jmri.jmrit.logix;

import java.util.List;
import java.util.TreeMap;
import jmri.DccLocoAddress;
import jmri.DccThrottle;
import jmri.InstanceManager;
import jmri.ThrottleListener;
import jmri.implementation.SignalSpeedMap;
import jmri.jmrit.logix.Engineer.RampData;
import jmri.jmrit.roster.Roster;
import jmri.jmrit.roster.RosterEntry;
import jmri.jmrit.roster.RosterSpeedProfile;
import jmri.jmrit.roster.RosterSpeedProfile.SpeedStep;
import jmri.util.ThreadingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedUtil implements ThrottleListener {

    private DccLocoAddress _dccAddress;
    private String _rosterId;        // Roster title for train
    private RosterEntry _rosterEntry;
    private Warrant _warrant;

//    private TreeMap<Integer, SpeedStep> _speedTree;
    private DccThrottle _throttle;
    private RosterSpeedProfile _speedProfile;
    private SignalSpeedMap _signalSpeedMap;

    private static float SCALE_FACTOR = 140; // divided by _scale, gives a rough correction for track speed

    public SpeedUtil(Warrant war) {
        _warrant = war;
    }
    
    public RosterEntry getRosterEntry() {
        if (_rosterEntry == null) {
            _rosterEntry = Roster.getDefault().entryFromTitle(_rosterId);
        }
        return _rosterEntry;
    }

    public String getTrainId() {
        return _rosterId;
    }

    public void setTrainId(String id) {
        _rosterId = id;
    }
    
    public DccLocoAddress getDccAddress() {
        if (_dccAddress == null) {
            if (_rosterEntry != null) {
                _dccAddress = _rosterEntry.getDccLocoAddress();
            } else {
                setDccAddress(_rosterId);
            }
        }
        return _dccAddress;            
    }
    
    public String getAddress() {
        if (_dccAddress != null) {
            return _dccAddress.toString();
        }
        return null;
    }
    
    protected void setDccAddress(DccLocoAddress dccAddr) {
        _dccAddress = dccAddr;
    }

   /**
     * Sets dccAddress and will fetch RosterEntry if one exists
     * @param id address as a String, either RosterTitle or decoder address
     * @return true if address found for id
     */
    public boolean setDccAddress(String id) {
        _rosterEntry = Roster.getDefault().entryFromTitle(id);
        if (_rosterEntry == null) {
            int index = id.indexOf('(');
            String numId;
            if (index >= 0) {
                numId = id.substring(0, index);
            } else {
                numId = id;
            }
            try {
                List<RosterEntry> l = Roster.getDefault().matchingList(null, null, numId, null, null, null, null);
                if (l.size() > 0) {
                    _rosterEntry = l.get(0);
                    if (_rosterId == null) {
                        // In some systems, such as Maerklin MFX or ESU ECOS M4, the DCC address is always 0.
                        // That should not make us overwrite the _trainId.
                        _rosterId = _rosterEntry.getId();
                    }
                    _dccAddress = _rosterEntry.getDccLocoAddress();           
                } else {
                    _rosterEntry = null;
                    boolean isLong = true;
                    if ((index + 1) < id.length()
                            && (id.charAt(index + 1) == 'S' || id.charAt(index + 1) == 's')) {
                        isLong = false;
                    }
                    int num = Integer.parseInt(numId);
                    _dccAddress = new DccLocoAddress(num, isLong);
                    if (_rosterId == null) {
                        _rosterId = _dccAddress.toString();                        
                    }
               }
            } catch (NumberFormatException e) {
                _dccAddress = null;
                return false;
            }            
        } else {
            _rosterId = id;
            _dccAddress = _rosterEntry.getDccLocoAddress();           
        }
        return true;
    }

    protected String acquireThrottle() {
        if (_dccAddress == null)  {
            return Bundle.getMessage("NoAddress", _warrant.getDisplayName());
        }
        jmri.ThrottleManager tm = InstanceManager.getNullableDefault(jmri.ThrottleManager.class);
        if (tm==null) {
            return Bundle.getMessage("noThrottle", _warrant.getDisplayName());
        } else {
            if (!tm.requestThrottle(_dccAddress.getNumber(), _dccAddress.isLongAddress(), this)) {
                return Bundle.getMessage("trainInUse", _dccAddress.getNumber());
            }           
        }
        if(log.isDebugEnabled()) log.debug("Throttle at {} requested for warrant {}",
                _dccAddress.toString(), _warrant.getDisplayName());          
        return null;
    }

    @Override
    public void notifyThrottleFound(DccThrottle throttle) {
        if (throttle == null) {
            ThreadingUtil.runOnLayout(() -> {
                _warrant.abortWarrant("notifyThrottleFound: null throttle(?)!");
                _warrant.fireRunStatus("throttleFail", null, Bundle.getMessage("noThrottle", _warrant.getDisplayName()));
            });
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("notifyThrottleFound for address= {}, class= {}, warrant {}", 
                    throttle.getLocoAddress().toString(), throttle.getClass().getName(), _warrant.getDisplayName());
        }

        _throttle = throttle;
        ThreadingUtil.runOnLayout(() -> {
            _warrant.startupWarrant();
            _warrant.runWarrant(throttle);
        });
    }   //end notifyThrottleFound

    protected void releaseThrottle() {
        if (_throttle != null) {
            jmri.ThrottleManager tm = InstanceManager.getNullableDefault(jmri.ThrottleManager.class);
            if (tm != null) {
                tm.releaseThrottle(_throttle, this);
            } else {
                log.error(Bundle.getMessage("noThrottle", _warrant.getDisplayName()));
            }
            _throttle = null;
        }
    }

    @Override
    public void notifyFailedThrottleRequest(DccLocoAddress address, String reason) {
        ThreadingUtil.runOnLayout(() -> {
            _warrant.abortWarrant( Bundle.getMessage("noThrottle", (reason +" "+_warrant.getDisplayName())));
            _warrant.fireRunStatus("throttleFail", null, reason);
        });
    }
    
    protected void stopRun(boolean updateSpeedProfile) {
        releaseThrottle();
        if (updateSpeedProfile && _rosterEntry!=null && _speedProfile!=null) {
            _rosterEntry.setSpeedProfile(_speedProfile);
            Roster.getDefault().writeRoster();
        }
    }

    protected float getStepIncrement() {
        return _signalSpeedMap.getStepIncrement();
    }
    protected int getStepDelay() {
        return _signalSpeedMap.getStepDelay();
    }
    
    /**
     * Calculates the scale speed of the current throttle setting for display
     * @param speedType name of current speed
     * @return text message
     */
//    @SuppressFBWarnings(value="IS2_INCONSISTENT_SYNC", justification="speed type name in message is ok")
    public String getSpeedMessage(String speedType) {
        float curSpeed = _throttle.getSpeedSetting();
        float speed = getTrackSpeed(curSpeed) * _signalSpeedMap.getLayoutScale();

        String units;
        if (_signalSpeedMap.getInterpretation() == SignalSpeedMap.SPEED_KMPH) {
            units = "Kmph";
            speed = speed * 3.6f;
        } else {
            units = "Mph";
            speed = speed * 2.2369363f;
        }
        return Bundle.getMessage("atSpeed", speedType, Math.round(speed), units);
    }


    /************************ runtime speed needs ************************/
    
    protected void makeSpeedTree() {
        _speedProfile = new RosterSpeedProfile(null);
//        _speedTree = new TreeMap<Integer, SpeedStep>();
//        _speedProfile.setSpeed(0, 0.0f, 0.0f);

        if (_rosterEntry!=null) {
            RosterSpeedProfile speedProfile = _rosterEntry.getSpeedProfile();
            if (speedProfile!=null) { // make copy of tree
    /*            TreeMap<Integer, SpeedStep> speedtree = _speedProfile.getProfileSpeeds();
                Set<java.util.Map.Entry<Integer, SpeedStep>> entries = _speedProfile.getProfileSpeeds().entrySet();
                java.util.Iterator<Map.Entry<Integer, SpeedStep>> iter = entries.iterator();
                while (iter.hasNext()) {
                    Map.Entry<Integer, SpeedStep> entry = iter.next();
                    SpeedStep ss = entry.getValue();
                    _speedProfile.setSpeed(entry.getKey(), ss.getForwardSpeed(), ss.getReverseSpeed());
                }*/
                
                TreeMap<Integer, SpeedStep> speedtree = new TreeMap<Integer, SpeedStep> ();
                speedtree.putAll(speedProfile.getProfileSpeeds());
                _speedProfile.setProfileSpeeds(speedtree);
 //           } else {
 //               _speedProfile.setSpeed(0, 0.0f, 0.0f);
            }
//        } else {
//            _speedProfile.setSpeed(0, 0.0f, 0.0f);            
        }

        _signalSpeedMap = jmri.InstanceManager.getDefault(SignalSpeedMap.class);
    }

    // return a boolean so minSpeedType() can return a non-null String if possible
    protected boolean secondGreaterThanFirst(String speed1, String speed2) {
        if (speed2 == null) {
            return false;
        }
        if (speed1 == null) {
            return true;
        }
        float s1 = _signalSpeedMap.getSpeed(speed1);
        float s2 = _signalSpeedMap.getSpeed(speed2);
        return (s1 < s2);
    }

    protected float modifySpeed(float tSpeed, String sType) {
//        if (log.isTraceEnabled()) log.trace("modifySpeed speed= {} for SpeedType= \"{}\"", tSpeed, sType);
        if (sType.equals(Warrant.Stop)) {
            return 0.0f;
        }
        if (sType.equals(Warrant.EStop)) {
            return -1.0f;
        }
        float throttleSpeed = tSpeed;       // throttleSpeed is a throttle setting
        if (sType.equals(Warrant.Normal)) {
            return throttleSpeed;
        }
        float signalSpeed = _signalSpeedMap.getSpeed(sType);

        switch (_signalSpeedMap.getInterpretation()) {
            case SignalSpeedMap.PERCENT_NORMAL:
                throttleSpeed *= signalSpeed / 100;      // ratio of normal
                break;
            case SignalSpeedMap.PERCENT_THROTTLE:
                signalSpeed = signalSpeed / 100;            // ratio of full throttle setting
                if (signalSpeed < throttleSpeed) {
                    throttleSpeed = signalSpeed;
                }
                break;

            case SignalSpeedMap.SPEED_MPH:          // convert miles per hour to track speed
                signalSpeed = signalSpeed / _signalSpeedMap.getLayoutScale();
                signalSpeed = signalSpeed / 2.2369363f;  // layout track speed mph as mm/ms
                float trackSpeed = getTrackSpeed(throttleSpeed);
                if (signalSpeed < trackSpeed) {
                    throttleSpeed = getThrottleSetting(signalSpeed);
                }
                break;

            case SignalSpeedMap.SPEED_KMPH:
                signalSpeed = signalSpeed / _signalSpeedMap.getLayoutScale();
                signalSpeed = signalSpeed / 3.6f;  // layout track speed mm/ms for kmph
                trackSpeed = getTrackSpeed(throttleSpeed);
                if (signalSpeed < trackSpeed) {
                    throttleSpeed = getThrottleSetting(signalSpeed);
                }
                break;
            default:
                log.error("Unknown speed interpretation {}", _signalSpeedMap.getInterpretation());
                throw new java.lang.IllegalArgumentException("Unknown speed interpretation " + _signalSpeedMap.getInterpretation());
        }
        if (log.isTraceEnabled()) log.trace("modifySpeed: from {}, to {}, signalSpeed= {} using interpretation {}",
                tSpeed, throttleSpeed, signalSpeed, _signalSpeedMap.getInterpretation());
        return throttleSpeed;
    }

    // return millimeters per millisecond (= meters/sec)
    private float getTrackSpeed(float throttleSetting) {
        // Note SpeedProfile uses milliseconds per second.
        float speed = _speedProfile.getSpeed(throttleSetting, _throttle.getIsForward()) / 1000;
        if (speed < 0.0f) {
            speed = throttleSetting *_signalSpeedMap.getDefaultThrottleFactor() * _signalSpeedMap.getLayoutScale() / SCALE_FACTOR;
        }
        if (log.isTraceEnabled()) log.trace("getTrackSpeed for setting= {}, speed= {}, warrant {}",
                    throttleSetting, speed, _warrant.getDisplayName());
        return speed;
    }

    private float getThrottleSetting(float trackSpeed) {
        float setting = _speedProfile.getThrottleSetting(trackSpeed, _throttle.getIsForward());
        if (setting < 0.0001f) {
            setting = trackSpeed * _signalSpeedMap.getLayoutScale() / (SCALE_FACTOR *_signalSpeedMap.getDefaultThrottleFactor());
        }
        return setting;
    }

    protected float getDistanceTraveled(float speedSetting, String speedtype, long time) {
        float throttleSetting = modifySpeed(speedSetting, speedtype);
        return getTrackSpeed(throttleSetting) * time;
    }

    protected long getTimeForDistance(float throttleSetting, float distance) {
        float speed = getTrackSpeed(throttleSetting);
        return (long) (distance/speed);
    }

    protected RampData rampLengthForSpeedChange(float curSpeed, String curSpeedType, String toSpeedType) {
        if (curSpeedType.equals(toSpeedType)) {
            return new RampData(0.0f, 0);
        }
        float fromSpeed = modifySpeed(curSpeed, curSpeedType);
        float toSpeed = modifySpeed(curSpeed, toSpeedType);
        if (toSpeed > fromSpeed) {
            float tmp = fromSpeed;
            fromSpeed = toSpeed;
            toSpeed = tmp;
        }
        float rampLength = 0.0f;
        float delta = _signalSpeedMap.getStepIncrement();
        int incr = _signalSpeedMap.getStepDelay();
        float speed = fromSpeed;
        int steps = 0;
        while (speed >= toSpeed) {
            float dist = getTrackSpeed(speed - delta / 2) * incr;
            if (dist <= 0.0f) {
                break;
            }
            speed -= delta;
            if (speed >= toSpeed) {
                rampLength += dist;
            } else {
                rampLength += (speed+delta - toSpeed) * dist / delta;
            }
            steps++;
        }
        int rampTime = incr*steps;
        if (log.isTraceEnabled()) log.trace("rampLengthForSpeedChange()= {} in {}ms for speed= {}, {} to {}, speed= {}",
                rampLength, rampTime, fromSpeed, curSpeedType, toSpeedType, toSpeed);
        return new RampData(rampLength, rampTime);   // add 1cm for safety (all scales)
    }
    
    synchronized protected float getSpeed() {
        float speed = _throttle.getSpeedSetting();
        if (speed < 0.0) {
            speed = 0.0f;
            _throttle.setSpeedSetting(speed);
        }
        return speed;
    }

    /*************** dynamic calibration ***********************/

    long _timeAtSpeed;
    float _distanceTravelled;
    float _settingsTravelled;
    long _changetime;
    int _numchanges;
    BlockOrder _blkOrder;
    
    protected void enteredBlock(BlockOrder blkOrder) {
        speedChange();
        float aveSpeed = _distanceTravelled / _timeAtSpeed;
        aveSpeed *= 1000;   // SpeedProfile is mm/sec
        float aveThrottle = _settingsTravelled / _timeAtSpeed;

        if (blkOrder != null) {
            boolean isForward = _throttle.getIsForward();
            float length = blkOrder.getPath().getLengthMm();
            if (length <= 0) {
                log.warn("Block {} does not have a length for path {}",
                        blkOrder.getBlock().getDisplayName(), blkOrder.getPathName());
            }
            float speed = _speedProfile.getSpeed(aveThrottle, isForward);
            if (log.isTraceEnabled()) {
                log.trace("AveThrottle= {}, distance= {}, time = {}. Path length = {}",
                        aveThrottle, _distanceTravelled, _timeAtSpeed, length);
                log.trace("{} speed changes. Speeds: SpeedProfile= {}, aveSpeed= {}, block= {}",
                        _numchanges, speed, aveSpeed, blkOrder.getBlock().getDisplayName());
            }
            if (length > 0.0f) {
                if (speed <= 0.0f) {
                    speed = length/_timeAtSpeed;
                } else {
                    speed = (speed + aveSpeed)/2;
                }
                if (isForward) {
                    _speedProfile.setForwardSpeed(aveThrottle, speed);
                } else {
                    _speedProfile.setReverseSpeed(aveThrottle, speed);            
                }
            }
        }
        _blkOrder = blkOrder;
        _timeAtSpeed = 0;
        _distanceTravelled = 0.0f;
        _settingsTravelled = 0.0f;
        _numchanges = 0;
    }
    
    protected void speedChange() {
        long time = System.currentTimeMillis();
        float throttleSetting = getSpeed();
        long elapsedTime = time - _changetime;
        if (throttleSetting > 0.0f) {
            _timeAtSpeed += elapsedTime;
            _distanceTravelled += getTrackSpeed(throttleSetting) * elapsedTime;
            _settingsTravelled += throttleSetting * elapsedTime;
        }
        _numchanges++;
        _changetime = time;
    }

    private final static Logger log = LoggerFactory.getLogger(SpeedUtil.class);
}