package jmri.jmrit.logix;

import jmri.jmrit.roster.RosterEntry;
import jmri.jmrit.roster.RosterSpeedProfile;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Paul Bender Copyright (C) 2017	
 */
public class SpeedProfilePanelTest {

    @Test
    public void testCTor() {
        RosterSpeedProfile rsp = new RosterSpeedProfile(new RosterEntry());
        SpeedProfilePanel t = new SpeedProfilePanel(rsp, true);
        Assert.assertNotNull("exists",t);
    }

    // The minimal setup for log4J
    @Before
    public void setUp() {
        jmri.util.JUnitUtil.setUp();
    }

    @After
    public void tearDown() {
        jmri.util.JUnitUtil.tearDown();
    }

    // private final static Logger log = LoggerFactory.getLogger(SpeedProfilePanelTest.class);

}
