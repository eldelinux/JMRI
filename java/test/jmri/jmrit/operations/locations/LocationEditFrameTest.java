//LocationEditFrameTest.java
package jmri.jmrit.operations.locations;

import java.io.File;
import java.util.List;
import java.util.Locale;
import jmri.jmrit.operations.rollingstock.engines.EngineManagerXml;
import jmri.jmrit.operations.routes.RouteManagerXml;
import jmri.jmrit.operations.setup.OperationsSetupXml;
import jmri.jmrit.operations.trains.TrainManagerXml;
import jmri.util.JmriJFrame;
import junit.extensions.jfcunit.eventdata.MouseEventData;
import junit.extensions.jfcunit.finder.AbstractButtonFinder;
import junit.extensions.jfcunit.finder.DialogFinder;
import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Tests for the Operations Locations GUI class
 *
 * @author	Dan Boudreau Copyright (C) 2009
 * @version $Revision$
 */
public class LocationEditFrameTest extends jmri.util.SwingTestCase {

    final static int ALL = Track.EAST + Track.WEST + Track.NORTH + Track.SOUTH;

    public void testLocationEditFrame() {
        LocationEditFrame f = new LocationEditFrame();
        f.setTitle("Test Add Location Frame");
        f.initComponents(null);

        f.locationNameTextField.setText("New Test Location");
        //f.addLocationButton.doClick();
        getHelper().enterClickAndLeave(new MouseEventData(this, f.addLocationButton));

        LocationManager lManager = LocationManager.instance();
        Assert.assertEquals("should be 6 locations", 6, lManager.getLocationsByNameList().size());
        Location newLoc = lManager.getLocationByName("New Test Location");

        Assert.assertNotNull(newLoc);

        // add a yard track
        getHelper().enterClickAndLeave(new MouseEventData(this, f.addYardButton));

        // add an interchange track
        getHelper().enterClickAndLeave(new MouseEventData(this, f.addInterchangeButton));

        // add a staging track
        getHelper().enterClickAndLeave(new MouseEventData(this, f.addStagingButton));

        // add a yard track
        getHelper().enterClickAndLeave(new MouseEventData(this, f.addYardButton));

        f.locationNameTextField.setText("Newer Test Location");
        getHelper().enterClickAndLeave(new MouseEventData(this, f.saveLocationButton));

        Assert.assertEquals("changed location name", "Newer Test Location", newLoc.getName());

        // test delete button
        getHelper().enterClickAndLeave(new MouseEventData(this, f.deleteLocationButton));
        Assert.assertEquals("should be 6 locations", 6, lManager.getLocationsByNameList().size());
        // confirm delete dialog window should appear
        pressDialogButton(f, "Yes");
        // location now deleted
        Assert.assertEquals("should be 5 locations", 5, lManager.getLocationsByNameList().size());

        f.dispose();
    }

    @SuppressWarnings("unchecked")
    private void pressDialogButton(JmriJFrame f, String buttonName) {
        //  (with JfcUnit, not pushing this off to another thread)			                                            
        // Locate resulting dialog box
        List<javax.swing.JDialog> dialogList = new DialogFinder(null).findAll(f);
        javax.swing.JDialog d = dialogList.get(0);
        // Find the button
        AbstractButtonFinder finder = new AbstractButtonFinder(buttonName);
        javax.swing.JButton button = (javax.swing.JButton) finder.find(d, 0);
        Assert.assertNotNull("button not found", button);
        // Click button
        getHelper().enterClickAndLeave(new MouseEventData(this, button));
    }

    private void loadLocations() {
        // create 5 locations
        LocationManager lManager = LocationManager.instance();
        Location l1 = lManager.newLocation("Test Loc E");
        l1.setLength(1001);
        Location l2 = lManager.newLocation("Test Loc D");
        l2.setLength(1002);
        Location l3 = lManager.newLocation("Test Loc C");
        l3.setLength(1003);
        Location l4 = lManager.newLocation("Test Loc B");
        l4.setLength(1004);
        Location l5 = lManager.newLocation("Test Loc A");
        l5.setLength(1005);

    }

    // Ensure minimal setup for log4J
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        apps.tests.Log4JFixture.setUp();

        // set the locale to US English
        Locale.setDefault(Locale.ENGLISH);

        // Repoint OperationsSetupXml to JUnitTest subdirectory
        OperationsSetupXml.setOperationsDirectoryName("operations" + File.separator + "JUnitTest");
        // Change file names to ...Test.xml
        OperationsSetupXml.instance().setOperationsFileName("OperationsJUnitTest.xml");
        RouteManagerXml.instance().setOperationsFileName("OperationsJUnitTestRouteRoster.xml");
        EngineManagerXml.instance().setOperationsFileName("OperationsJUnitTestEngineRoster.xml");
        LocationManagerXml.instance().setOperationsFileName("OperationsJUnitTestLocationRoster.xml");
        LocationManagerXml.instance().setOperationsFileName("OperationsJUnitTestLocationRoster.xml");
        TrainManagerXml.instance().setOperationsFileName("OperationsJUnitTestTrainRoster.xml");

        // clear out previous locations
        LocationManager.instance().dispose();

        loadLocations();
    }

    public LocationEditFrameTest(String s) {
        super(s);
    }

    // Main entry point
    static public void main(String[] args) {
        String[] testCaseName = {"-noloading", LocationEditFrameTest.class.getName()};
        junit.swingui.TestRunner.main(testCaseName);
    }

    // test suite from all defined tests
    public static Test suite() {
        TestSuite suite = new TestSuite(LocationEditFrameTest.class);
        return suite;
    }

    // The minimal setup for log4J
    @Override
    protected void tearDown() throws Exception {
        apps.tests.Log4JFixture.tearDown();
        super.tearDown();
    }
}