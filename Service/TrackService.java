package Service;

import com.jcraft.jsch.JSchException;
import controller.TrackOperations;
import exceptions.ImageNotConfiguredException;
import exceptions.NetworkException;
import exceptions.ObjectStateException;
import model.*;
import model.Point;
import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import org.sikuli.script.*;
import org.sikuli.script.Image;
import org.xml.sax.SAXException;
import util.CmdLine;
import util.SSHManager;

import javax.imageio.ImageIO;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;


enum TrackState{
     NORMAL,
    BLOCKED,
    DISREGARDED,
}

public class TrackService {
    public static TrackService trackService;

    public  static ScreenService screenService;
    public List<Track> tracks;
    StationMode stationMode = StationMode.getInstance();
    public List<BerthInfo> berths;
    InterlockingService interlockingService;
    View currentView;
    SSHManager sshManager;
    SSHManager sshManager_sim;
    CoordinateService coordinateService;
    private static final Logger logger = Logger.getLogger(TrackService.class.getName());

    TrackService() throws ParserConfigurationException, IOException, SAXException {
        screenService = ScreenService.getInstance();
        interlockingService = InterlockingService.getInstance();
        coordinateService = CoordinateService.getInstance();
        generateTracks();
    }

    private void generateTracks() throws ParserConfigurationException, IOException, SAXException {

            tracks = new ArrayList<Track>();
            berths = util.XMLParser.parseBerth("RailwayBerths.xml");

            List<TrackInfo> al = util.XMLParser.parseTracks("RailwayTracks.xml");
            for (TrackInfo eachTrack : al) {
                if (!Objects.equals(eachTrack.getCircuitName(), "")) {
                    String trackName[] = eachTrack.getCircuitName().split("/");
                    Location location = coordinateService.getCoordinatedById(eachTrack.getCircuitName());
                    Location coordinate = coordinateService.getScreenCoordinatedById(eachTrack.getCircuitName());
                    tracks.add(new Track(eachTrack.getTrackId(), trackName[1], eachTrack.getCircuitName(),
                            eachTrack.getTrackNormalDown(),
                            eachTrack.getTrackNormalUp(),
                            eachTrack.getTrackReverseDown(),
                            eachTrack.getTrackReverseUp(),
                            this.interlockingService.getInterlockingByName(trackName[0]), location, coordinate));
                }
            }
        }


    public static TrackService getInstance() throws ParserConfigurationException, IOException, SAXException {
        if(trackService==null){
            trackService = new TrackService();
            return trackService;
        }
        else {return trackService;}
    }
    public List<Track> getTracks(){

        return this.tracks;
    }

    private void setScreen(@NotNull String object, String action) throws InterruptedException {
        currentView = ViewManager.getInstance().getCurrentView();
        for (Device device : stationMode.getAllDevice()) {
            String ipAddress = !device.getIpAddress().isEmpty() ?"localhost":device.getIpAddress();
            CmdLine.getResponseSocketDifferent(currentView.getName(), object, "Track", ipAddress,device.getType());
        }
        Location centerLocation = new Location(currentView.getCoordinateX(), currentView.getCoordinateY());
        Thread.sleep(1000);
        if (!Objects.equals(action, "nonClickable")) {
            if (Objects.equals(action, "cancel")) {
                centerLocation.rightClick();
            } else {
                centerLocation.click();
            }
            Thread.sleep(2000);

        }
    }
    public List<Track> getUpdatedTrack(){
        if (stationMode.getFile() != null) {
            List<String> values = util.XMLParser.parseCSV(stationMode.getFile().getAbsolutePath()); // Assuming parseCSV returns List<String> and getFile() returns a File object.
            List<Track> matchingPoint = this.tracks.stream()
                    .filter(track -> values.contains(track.getId()) || values.contains(track.getCircuitName()))
                    .collect(Collectors.toList());
            return matchingPoint;
        }else{
            return this.tracks;
        }
    }

    private void takeScreenShot(String name, Boolean fullScreen, String BLK , String PK) throws AWTException, JSchException, IOException, InterruptedException {
        int valueA= 200;
        int valueB=500;
        //ScreenImage screenImage = getScreenImage(fullScreen,valueA,valueB);
        BufferedImage screenImage = getScreenImage(fullScreen,valueA,valueB, BLK, PK);
        File directory = new File("TrackImages");
        if (!directory.exists()) {
            directory.mkdirs(); // Create directory if it does not exist
        }

        // Correct the path to include the parent directory
        String subdirectoryName = name.split(":")[0].replace("/", "");
        File subdirectory = new File(directory, subdirectoryName);

        if (!subdirectory.exists()) {
            subdirectory.mkdirs();
        }
        String formatName = "PNG"; // Image format
        String fileName = name.split(":")[1].replace("/", "") + ".png"; // Construct filename
        String outputPath = subdirectory + File.separator + fileName; // Construct the full output path

        // Now use ImageIO.write with the correct arguments
        ImageIO.write(screenImage, formatName, new File(outputPath));



    }



    private BufferedImage getScreenImage(boolean fullScreen,int valueA, int valueB,String BLK , String PK) {
        BufferedImage combined = null;
        Graphics2D g2 = null;
        currentView = ViewManager.getInstance().getCurrentView();

        int xMain = currentView.getCoordinateX() ;
        int yMain = currentView.getCoordinateY() ;

        BufferedImage img1 = getBufferedImage(xMain,yMain,valueA,valueB,fullScreen);
        if (stationMode.isTCWindowRequired()) {
            int xTc = stationMode.getTcDevice().getScreenDeatils().getCoordinateX() ;
            int yTc = stationMode.getTcDevice().getScreenDeatils().getCoordinateY() ;
            BufferedImage img2 = getBufferedImage(xTc,yTc,valueA, valueB,fullScreen);

            int width = img1.getWidth() + img2.getWidth() + 10; // 10 for the border
            int height = Math.max(img1.getHeight(), img2.getHeight());
            combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            g2 = combined.createGraphics();
            g2.drawImage(img1, null, 0, 0);
            // Draw the border
            g2.setColor(Color.BLACK); // Border color
            g2.fillRect(img1.getWidth(), 0, 10, height); // 10 pixels wide border
            // Draw the second image
            g2.drawImage(img2, null, img1.getWidth() + 10, 0); // Starts after the border

        }else{
            g2 = img1.createGraphics();
        }


        g2.setFont(new Font("Arial", Font.BOLD, 15));
        g2.setColor(Color.RED);

        // Determine the position where you want to draw the text
        int textX = 10; // Example x position
        int textY = 20; // Example y position, adjust as needed
        String text= BLK +" " + PK;
        // Draw the text
        g2.drawString(text, textX, textY);
        g2.dispose(); // Clean up

        return stationMode.isTCWindowRequired()? combined:img1;

    }

    private BufferedImage getBufferedImage(int corrX, int corrY,int valueA, int valueB,boolean fullScreen) {
        ScreenImage imageScreen = null;
        Screen screenshot = (Screen) new Location(corrX,
                corrY).getScreen();
        if (!fullScreen) {
            int x = corrX - valueA;
            int y = corrY - valueA;

            imageScreen = screenshot.capture(x, y, valueB, valueB);
        }
        return  fullScreen?  screenshot.capture().getImage():imageScreen.getImage();
    }
    public List<String> getBerthByTrackId(String trackId){
        List<String> currentBerth = new ArrayList<>();
        for(BerthInfo berth : berths){
            Track  track =getTrackById(trackId);
            if(Objects.equals(berth.getTrackId(), track.getCircuitName()) ||Objects.equals(berth.getTrackId(), track.getId()) ){
                if(berth.getOverView() == null){
                    currentBerth.add(berth.getId());
                }else {
                    currentBerth.add(berth.getId());
                    currentBerth.addAll(berth.getOverView());

                }

                break;
            }
        }

        return currentBerth;

    }




    public Track getTrackById(String trackId){
        Track currentTrack = null;
        for(Track track : this.getTracks()){
            if(Objects.equals(track.getCircuitName(), trackId) || Objects.equals(track.getId(), trackId)){
                currentTrack = track;
                break;
            }
        }
        return currentTrack;

    }

    public Location getCoordinateById(String trackId){
        Location currentCorrdinate = null;
        for(Track track : this.getTracks()){
            if(Objects.equals(track.getCircuitName(), trackId)){
                currentCorrdinate = track.getScreenCoordinates();
                break;
            }
        }
        return currentCorrdinate;

    }


    public void blockTrackById(String trackId) throws ImageNotConfiguredException, FindFailed, NetworkException, ParserConfigurationException, SAXException, ObjectStateException, IOException, InterruptedException {
        Track track = this.getTrackById(trackId);
        this.blockTrack(track);

    }



    public void unblockTrackById(String trackId) throws FindFailed, ParserConfigurationException, SAXException, ObjectStateException, NetworkException, FileNotFoundException, InterruptedException {

        Track track = this.getTrackById(trackId);
        this.unblockTrack(track);

    }

    public void setDisregardOnTrackById(String trackId) throws FindFailed, ObjectStateException, NetworkException, ParserConfigurationException, SAXException, FileNotFoundException, InterruptedException {

        Track track = this.getTrackById(trackId);
        this.setDisregardOn(track);

    }
    public List setDisregardOffTrackById(String trackId) throws FindFailed, ObjectStateException, NetworkException, ParserConfigurationException, SAXException, FileNotFoundException, InterruptedException {

        Track track = this.getTrackById(trackId);
        List result = null;
        //result = IPEngineService.getMatch(track,track.getImagePathDisregardedState(),null);
        this.setDisregardOff(track);
        return result;

    }
    public void configureTrackById(String trackId) throws IOException, FindFailed, InterruptedException {
        this.configureTrack(getTrackById(trackId));
    }
    public void blockTrack(Track track) throws FindFailed, InterruptedException, IOException {
        //Screen screen = (Screen) track.getLocation().getScreen();
        logger.info("Attempting to Block Track "+track.getCircuitName());
        //screenService.findScreen(screen,ScreenCoordinate);
        setScreen(track.getCircuitName(),"click");
        Screen screen = (Screen) new Location(currentView.getCoordinateX(),currentView.getCoordinateY()).getScreen();

        try{// in build
            screen.wait(System.getProperty("user.dir")+"/src/resources/track_block.png",2).click();
            Thread.sleep(2000);
            takeScreenShot(track.getCircuitName()+":block",false,"track","block");
        }catch(FindFailed ff){
            throw new FindFailed("Dropdown Menu option was not located. "+ff.getMessage());
        } catch (JSchException | AWTException e) {
            throw new RuntimeException(e);
        }
    }




    public void unblockTrack(Track track) throws FindFailed, InterruptedException {
        //Screen screen = (Screen) track.getLocation().getScreen();
        logger.info("Attempting to unblock Track "+track.getCircuitName());
        //track.getLocation().click();
        setScreen(track.getCircuitName(),"click");
        Screen screen = (Screen) new Location(currentView.getCoordinateX(),currentView.getCoordinateY()).getScreen();
        try{
            Match match1 =screen.wait(System.getProperty("user.dir")+"/src/resources/track_unblock.png");
            match1.click();
            Thread.sleep(2);
            screen.wait(System.getProperty("user.dir")+"/src/resources/Confirmation_yes.png",4).click();
        }catch(FindFailed ff){
            logger.warning("This track cannot be unblocked:Dropdown Menu option was not located");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        logger.info("Completed unblocking Track "+track.getId());
    }
    public void setDisregardOn(Track track) throws FindFailed, InterruptedException {
        //Screen screen = (Screen) track.getLocation().getScreen();
        logger.info("Attempting to set the disregard on for Track "+track.getCircuitName());
        //track.getLocation().click();
        setScreen(track.getCircuitName(),"click");
        Screen screen = (Screen) new Location(currentView.getCoordinateX(),currentView.getCoordinateY()).getScreen();
        try{
            Match match = screen.wait(System.getProperty("user.dir")+"/src/resources/track_disregard.png",2);
            match.click();
            logger.info("Completed turning on disregard for Track "+track.getCircuitName());
            Thread.sleep(1000);
            takeScreenShot(track.getCircuitName()+":disregard",false,"track","disregard");

        }catch(FindFailed ff){
            System.out.println("This track cannot be disregarded:Dropdown Menu option was not located");
        } catch (JSchException | IOException | AWTException e) {
            throw new RuntimeException(e);
        }
    }
    public void setDisregardOff(Track track) throws FindFailed, InterruptedException {
        //Screen screen = (Screen) track.getLocation().getScreen();
        logger.info("Attempting to set the disregard off for Track:  "+track.getCircuitName());
        //track.getLocation().click();
        setScreen(track.getCircuitName(),"click");
        Screen screen = (Screen) new Location(currentView.getCoordinateX(),currentView.getCoordinateY()).getScreen();
        try{
            Match match = screen.wait(System.getProperty("user.dir")+"/src/resources/track_disregard.png",2);
            match.click();
            logger.info("Completed turning off the disregard for Track: "+track.getId());
        }catch(FindFailed ff){
            System.out.println("This track cannot be disregarded:Dropdown Menu option was not located");
        }
    }
    public void configureTrack(Track track) throws IOException, InterruptedException {
        try {
            int screenNumber = 0;
            Screen s = this.screenService.getScreens().get(screenNumber);
            Region region = s.selectRegion();
            Image.reload(track.getImagePath());
            region.getImage().save(track.getImagePath());
            logger.info("Blocking Track");
            //this.blockTrackByCli(track);
            Thread.sleep(4000);
            region.getImage().save(track.getImagePathBlockedState());
            logger.info("Calibration for blocked track complete");
            this.ublockTrackByCli(track);
            logger.info("Turning on disregard for the track ");
            this.setDisregardOnTrackByCli(track);
            Thread.sleep(4000);
            region.getImage().save(track.getImagePathDisregardedState());
            logger.info("Calibration for disregarded track complete");
            this.setDisregardOffByCli(track);
        }catch (NullPointerException ne){
            logger.info("There was an error configuring the object. PLease try again");
        }
    }


    public void blockTrackByCli(Track track) throws IOException, InterruptedException, FindFailed {
        ArrayList<String> al = new ArrayList<>();
        al.add("TKB");
        al.add(track.getId());
        CmdLine.send(al);
        Thread.sleep(1000);
    }
    public void setDisregardOnTrackByCli(Track track) throws IOException, InterruptedException {
        ArrayList<String> al = new ArrayList<>();
        al.add("TCD");
        al.add(track.getId());
        CmdLine.send(al);
        Thread.sleep(1000);
    }
    public void ublockTrackByCli(Track track) throws IOException, InterruptedException {
        ArrayList<String> al = new ArrayList<>();
        al.add("TKUI");
        al.add(track.getId());
        CmdLine.send(al);
        Thread.sleep(3000);
        al.clear();
        al.add("TKU");
        al.add(track.getId());
        CmdLine.send(al);
        Thread.sleep(3000);
    }
    public void setDisregardOffByCli(Track track) throws IOException, InterruptedException {
        ArrayList<String> al = new ArrayList<>();
        al.add("TCR");
        al.add(track.getId());
        CmdLine.send(al);
        Thread.sleep(3000);
    }


    public void dropSimTrackById(String o) throws JSchException, IOException, InterruptedException, AWTException, FindFailed {
        Track track= this.getTrackById(o);
        System.out.println(track.getCircuitName());
        if(!stationMode.isSimWindowRequired()) {
            setScreen(track.getCircuitName(), "cancel");
            Thread.sleep(2000);
            takeScreenShot(track.getCircuitName() + ":drop", false, "track", "drop");
            Thread.sleep(1500);
            setScreen(track.getCircuitName(), "cancel");
        }else{
            simDrop(track);
            Thread.sleep(1000);
            simPick(track);
        }

    }

    private void simDrop(Track track) throws InterruptedException, FindFailed {
        logger.info("Attempting to Drop Track "+track.getCircuitName());
        //screenService.findScreen(screen,ScreenCoordinate);
        setScreen(track.getCircuitName(),"click");
        Screen screen = (Screen) new Location(currentView.getCoordinateX(),currentView.getCoordinateY()).getScreen();

        try{// in build
            screen.wait(System.getProperty("user.dir")+"/src/resources/sim_command.png",3).click();
            Thread.sleep(1000);
            for (int i = 0; i < 4; i++) {
                screen.type(Key.DOWN);
            }
            screen.type(null,Key.ENTER);
            Thread.sleep(1500);
            takeScreenShot(track.getCircuitName()+":Drop",false,"track","DROP");
        }catch(FindFailed ff){
            throw new FindFailed("Dropdown Menu option was not located. "+ff.getMessage());
        } catch (JSchException | AWTException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void simPick(Track track) throws InterruptedException, FindFailed {
        logger.info("Attempting to Pick Track "+track.getCircuitName());
        //screenService.findScreen(screen,ScreenCoordinate);
        setScreen(track.getCircuitName(),"click");
        Screen screen = (Screen) new Location(currentView.getCoordinateX(),currentView.getCoordinateY()).getScreen();

        try{// in build
            screen.wait(System.getProperty("user.dir")+"/src/resources/sim_command.png",3).click();
            Thread.sleep(1000);
            for (int i = 0; i < 3; i++) {
                screen.type(Key.DOWN);
            }
            screen.type(null,Key.ENTER);

        }catch(FindFailed ff){
            throw new FindFailed("Dropdown Menu option was not located. "+ff.getMessage());
        }
    }


    private void simFail(Track track) throws InterruptedException, FindFailed {
        logger.info("Attempting to Drop Track "+track.getCircuitName());
        //screenService.findScreen(screen,ScreenCoordinate);
        setScreen(track.getCircuitName(),"click");
        Screen screen = (Screen) new Location(currentView.getCoordinateX(),currentView.getCoordinateY()).getScreen();

        try{// in build
            screen.wait(System.getProperty("user.dir")+"/src/resources/sim_command.png",3).click();
            Thread.sleep(1000);
            screen.type(Key.DOWN);
            screen.type(null,Key.ENTER);
            Thread.sleep(1500);
            takeScreenShot(track.getCircuitName()+":Fail",false,"track","block");
        }catch(FindFailed ff){
            throw new FindFailed("Dropdown Menu option was not located. "+ff.getMessage());
        } catch (JSchException | AWTException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void simUnFail(Track track) throws InterruptedException, FindFailed {
        logger.info("Attempting to Pick Track "+track.getCircuitName());
        //screenService.findScreen(screen,ScreenCoordinate);
        setScreen(track.getCircuitName(),"click");
        Screen screen = (Screen) new Location(currentView.getCoordinateX(),currentView.getCoordinateY()).getScreen();

        try{// in build
            screen.wait(System.getProperty("user.dir")+"/src/resources/sim_command.png",3).click();
            Thread.sleep(1000);
            for (int i = 0; i < 2; i++) {
                screen.type(Key.DOWN);
            }
            screen.type(null,Key.ENTER);

        }catch(FindFailed ff){
            throw new FindFailed("Dropdown Menu option was not located. "+ff.getMessage());
        }
    }


    private void trackOperation(String operation, @NotNull String trackId) throws JSchException, IOException, InterruptedException, AWTException {
        String trackGuido = trackId.replace("/", "");
        System.out.println(trackId + " track " + operation);
        String command = "/opt/fg/bin/clickSimTrk" + operation + ".sh 0 0 0 0 0 0 0 " + trackGuido;
        if (!stationMode.getSimIP().isEmpty()) {
            sshManager_sim = SSHManager.getInstance("sysadmin", "tcms2009", stationMode.getSimIP(), 22);
        }

        sshManager_sim.sendCommand(command);
       if(Objects.equals(operation, "Drop"))
           takeScreenShot(trackId+":drop",false,"track","drop");
    }

    public void pickSimTrackById(String o) throws JSchException, IOException, InterruptedException, AWTException {
        Track track= this.getTrackById(o);
        this.trackOperation("Pick",track.getId());
    }

    public void failTrackById(String o) throws FindFailed, InterruptedException {

        Track track= this.getTrackById(o);
        if(stationMode.isSimWindowRequired()) {
            simFail(track);
            Thread.sleep(1000);
            simUnFail(track);
        }else{
            System.out.println("Can only be performed in sim window.");
        }
    }

    public void unfailTrackById(String o) {
    }
}