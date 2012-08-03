/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cismap.scaleimageprovider;

import org.apache.log4j.Logger;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;

import java.text.DecimalFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Image Generator to generate scale images as well as images marking coordinates on the vertical or horizontal axis.
 *
 * @author   Benjamin Friedrich (benjamin.friedrich@cismet.de)
 * @version  $Revision$, $Date$
 */
public final class ScaleImageGenerator {

    //~ Static fields/initializers ---------------------------------------------

    private static final int SCALE_IMG_WIDTH = 170;           // base width of the generated scale image in px
    private static final int SCALE_IMG_HEIGHT = 25;           // base height of the generated scale image in px
    private static final int NUM_SCALE_UNIT_RECTANGLES = 5;   // number unit rectangles displayed in scale image
    private static final int SCALE_UNIT_MARGIN = 10;          // scalue unit margin in px
    private static final String SCALE_DECIMAL_FORMAT = "0.#"; // decimal format for representing scale unit values

    private static final int VERTICAL_AXIS_WIDTH = 25;    // base width of the image for the vertical axis in px
    private static final int HORIZONTAL_AXIS_HEIGHT = 25; // base height of the image for the horizontal axis in px

    private static final String FONT_NAME = "Arial";  // default font
    private static final int FONT_STYLE = Font.PLAIN; // default style
    private static final float AXIS_FONT_SIZE = 8f;

    private static final int STROKE_BASE_SIZE = 1; // base stroke size in px

    private static final double DPC = 2.54; // 1 dot per cm = 2,54 dpi

    private static final double JAVA_DEFAULT_RESOLUTION = 72.0; // Java default resoultion in DPI

    private static final Logger LOG = Logger.getLogger(ScaleImageGenerator.class);

    //~ Constructors -----------------------------------------------------------

    /**
     * Avoids creation of a new ScaleImageGenerator object.
     */
    private ScaleImageGenerator() {
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * Standard Graphics configuration needed in each image (scale, horizontal, vertical).
     *
     * @param   img  image whose graphic context shall be configured
     * @param   dpi  desired DPI
     *
     * @return  configured Graphics instance
     */
    private static Graphics2D configureImageGraphics(final BufferedImage img, final int dpi) {
        final Graphics2D g = img.createGraphics();

        // activate antialising
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // clear background
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, img.getWidth(), img.getHeight());

        // set color for drawing scale figure
        g.setColor(Color.BLACK);

        final double dpiScale = calculateDPIScale(dpi);

        // draw lines with width of 2px scaled accroding to given dpi
        final Stroke s = new BasicStroke((float)(STROKE_BASE_SIZE * dpiScale));
        g.setStroke(s);

        final Font font = new Font(FONT_NAME, FONT_STYLE, (int)Math.ceil(AXIS_FONT_SIZE * dpiScale));
        g.setFont(font);

        return g;
    }

    /**
     * Adjusts font size so that a text fits into the target width.
     *
     * @param   g             current graphics context
     * @param   targetWidth   target width (in px) in which the text shall fit
     * @param   currentWidth  current width (in px) which represents the text width in most cases
     *
     * @return  DOCUMENT ME!
     */
    private static boolean adjustFontSize(final Graphics2D g, final float targetWidth, final float currentWidth) {
        if (currentWidth > targetWidth) {
            final Font font = g.getFont();

            final float fontSize = font.getSize();
            final float newFontSize = fontSize * targetWidth / currentWidth;
            if (LOG.isDebugEnabled()) {
                LOG.debug("need scale font size. old font size=" + fontSize + " new font size=" + newFontSize);
            }

            g.setFont(font.deriveFont(newFontSize));

            return true;
        }

        return false;
    }

    /**
     * Returns grid size according to heuristic.
     *
     * @param   scale  scale in Centimeter
     *
     * @return  grid size
     */
    public static int getGridSize(final int scale) {
        if (scale < 500) {
            return 20;
        } else if (scale < 1000) {
            return 50;
        } else if (scale < 2000) {
            return 100;
        } else if (scale < 5000) {
            return 200;
        } else if (scale < 25000) {
            return 1000;
        } else if (scale < 50000) {
            return 2000;
        } else if (scale < 75000) {
            return 3000;
        } else if (scale < 100000) {
            return 4000;
        } else if (scale < 150000) {
            return 8000;
        } else if (scale < 250000) {
            return 12000;
        }

        return 20000;
    }

    /**
     * Generates scale image.<br/>
     * <br/>
     * <b>NOTE:</b> The bounding box has to be specified in a metric system such as EPSG:25832
     *
     * @param   minX       lower left x coordinate of bounding box
     * @param   minY       lower left y coordinate of bounding box
     * @param   maxX       upper right x coordinate of bounding box
     * @param   maxY       upper right y coordinate of bounding box
     * @param   mapWidth   width of map image in px
     * @param   mapHeight  height of map image in px
     * @param   dpi        desired quality of scale image
     *
     * @return  scale image
     */
    public static Image generateScaleImage(final double minX,
            final double minY,
            final double maxX,
            final double maxY,
            final int mapWidth,
            final int mapHeight,
            final int dpi) {
        final double dpiScale = calculateDPIScale(dpi);

        // determine scale unit in meter
        final double bbWidthInMeter = Math.abs(maxX - minX);
        final double bbWidthInCentimeter = bbWidthInMeter * 100.0;
        final double scaleWidth = bbWidthInCentimeter / (mapWidth / JAVA_DEFAULT_RESOLUTION * 2.54);
        final double scaleUnitInMeter = Math.round(scaleWidth * 100d) / 10000d;

        // calculate image heigth and image width respect to specified dpi
        final int imgWidth = (int)Math.floor(SCALE_IMG_WIDTH * dpiScale);
        final int imgHeight = (int)Math.floor(SCALE_IMG_HEIGHT * dpiScale);

        // ==================== CREATE IMAGE =======================================================

        final BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = configureImageGraphics(img, dpi);

        final int dpc = (int)Math.ceil(dpi / DPC);

        // rectangle representing 1 scale unit on the image
        final Rectangle unitRec = new Rectangle(0, imgHeight - (imgHeight / 2) - 1, dpc, imgHeight / 3);
        unitRec.translate(dpc / 2, 0);

        // rectangle representing separator for the drawn unitRecs
        final int sepHeight = unitRec.y / 6;
        final Rectangle separator = new Rectangle(unitRec.x, unitRec.y - sepHeight, 1, sepHeight);

        // font metrics to determine text size
        FontMetrics metrics = g.getFontMetrics();
        final int txtHeight = metrics.getHeight();
        final int txtYPos = separator.y - (txtHeight / 4);

        String labelTxt;
        int labelWidth;
        double scaleInc = scaleUnitInMeter;

        final DecimalFormat dc = new DecimalFormat(SCALE_DECIMAL_FORMAT);

        // check if max label width fits into unit rectangle. if it does not, down-scale the font size accordingly
        labelTxt = dc.format(scaleInc * NUM_SCALE_UNIT_RECTANGLES);
        labelWidth = metrics.stringWidth(labelTxt);

        if (adjustFontSize(g, unitRec.width - (2 * SCALE_UNIT_MARGIN), labelWidth)) {
            metrics = g.getFontMetrics();
        }

        for (int i = 0; i < NUM_SCALE_UNIT_RECTANGLES; i++) {
            // each second rectangle is filled with black color
            if (((i + 1) % 2) == 0) {
                g.fill(unitRec);
            }

            // draw outline of rectangle
            g.draw(unitRec);

            // move separator to end of unitRec
            separator.translate(dpc, 0);

            // draw text
            labelTxt = dc.format(scaleInc);
            labelWidth = metrics.stringWidth(labelTxt);

            g.drawString(labelTxt, separator.x - (labelWidth / 2), txtYPos);

            // draw separator
            g.draw(separator);

            // move unitRec to 1 cm along x-axis
            unitRec.translate(dpc, 0);

            // increment for next unit rectangle
            scaleInc += scaleUnitInMeter;
        }

        g.dispose();

        return img;
    }

    /**
     * Project real world coordinate to image coordinate.
     *
     * @param   realWorldCoordinate  real world coordinate
     * @param   imgHeightOrWidth     image height or width in px. For example, if the real world coordinate belongs to
     *                               the x-axis, the image width has to specified.
     * @param   minCoord             min coordinate (e.g min x)
     * @param   maxCoord             max coordinate (e.g max x)
     *
     * @return  DOCUMENT ME!
     */
    private static double projectRealWorldCoordToImageCoord(final double realWorldCoordinate,
            final double imgHeightOrWidth,
            final double minCoord,
            final double maxCoord) {
        final double realWorldDistanceInCentimer = Math.abs(minCoord - realWorldCoordinate) * 100;
        final double bbDeltaInCentimeter = Math.abs(maxCoord - minCoord) * 100;

        final double ratio = imgHeightOrWidth / ((bbDeltaInCentimeter * JAVA_DEFAULT_RESOLUTION) / DPC);

        // project it to image coordinate system
        return Math.round((((realWorldDistanceInCentimer * JAVA_DEFAULT_RESOLUTION) / DPC) * ratio));
    }

    /**
     * Determines all suitable coordinates. A coordinate is suitable, if it is divisable by the grid size and located
     * between minCoord (inclusive) and maxCoord (inclusive)
     *
     * @param   minCoord  min coordinate (e.g. min x)
     * @param   maxCoord  max coordinate (e.g. max x)
     * @param   gridSize  grid size in m
     *
     * @return  list of suitable real world coordinates
     */
    private static List<Double> findSuitableCoordinates(final double minCoord,
            final double maxCoord,
            final int gridSize) {
        final ArrayList<Double> coords = new ArrayList<Double>();

        // start coordinate has to be an integer and it has to be greater or equal to minCoord
        // (otherwise we would get a coordinate which is not displayed in map)
        int startCoord = (int)Math.ceil(minCoord);
        if ((startCoord % gridSize) != 0) {
            // round down to foregoing mark according to grid size
            // for example coordinate 5682123 becomes 5682000, if grid size is 1000
            startCoord = (startCoord / gridSize) * gridSize;

            // set start coordinate to first possible mark
            startCoord += gridSize;
        }

        // converts maxCoord to integer. NOTE: we use Math.floor() because Mat.ceil() can result to a coordinate
        // which is not displayed in map
        final int endCoord = (int)Math.floor(maxCoord);

        // TODO what if we have to set marks at the map boundaries?
        for (int c = startCoord; c <= endCoord; c += gridSize) {
            coords.add(Double.valueOf(c));
        }

        return coords;
    }

    /**
     * Calcualates the scale to scale size units according to desired DPI quality.
     *
     * @param   dpi  dpi
     *
     * @return  dpi scale
     */
    private static double calculateDPIScale(final int dpi) {
        return (double)dpi / (double)JAVA_DEFAULT_RESOLUTION;
    }

    /**
     * Generates image for marking coordinates on the vertical axis. This method is needed because the PrintingWidget
     * passes the scale as String.<br/>
     * <b>NOTE:</b> The bounding box has to be specified in a metric system such as EPSG:25832
     *
     * @param   minX       lower left x coordinate of bounding box
     * @param   minY       lower left y coordinate of bounding box
     * @param   maxX       upper right x coordinate of bounding box
     * @param   maxY       upper right y coordinate of bounding box
     * @param   mapWidth   width of map image in px
     * @param   mapHeight  height of map image in px
     * @param   dpi        desired quality of scale image
     * @param   scale      map scale in cm (the marks are set according to the grid derived from the scale). Note that
     *                     the passed String has to be parseable as Integer
     *
     * @return  image for vertical axis
     */
    public static Image generateVerticalAxis(final double minX,
            final double minY,
            final double maxX,
            final double maxY,
            final int mapWidth,   // in px
            final int mapHeight,  // in px
            final int dpi,
            final String scale) {
        return generateVerticalAxis(minX, minY, maxX, maxY, mapWidth, mapHeight, dpi, Integer.parseInt(scale));
    }

    /**
     * Generates image for marking coordinates on the vertical axis.<br/>
     * <b>NOTE:</b> The bounding box has to be specified in a metric system such as EPSG:25832
     *
     * @param   minX       lower left x coordinate of bounding box
     * @param   minY       lower left y coordinate of bounding box
     * @param   maxX       upper right x coordinate of bounding box
     * @param   maxY       upper right y coordinate of bounding box
     * @param   mapWidth   width of map image in px
     * @param   mapHeight  height of map image in px
     * @param   dpi        desired quality of scale image
     * @param   scale      map scale in cm (the marks are set according to the grid derived from the scale)
     *
     * @return  image for vertical axis
     */
    public static Image generateVerticalAxis(final double minX,
            final double minY,
            final double maxX,
            final double maxY,
            final int mapWidth,
            final int mapHeight,
            final int dpi,
            final int scale) {
        final int gridSize = getGridSize(scale);
        return generateVerticalAxisWithGrid(minX, minY, maxX, maxY, mapWidth, mapHeight, dpi, gridSize);
    }

    /**
     * Generates image for marking coordinates on the vertical axis.<br/>
     * <b>NOTE:</b> The bounding box has to be specified in a metric system such as EPSG:25832
     *
     * @param   minX       lower left x coordinate of bounding box
     * @param   minY       lower left y coordinate of bounding box
     * @param   maxX       upper right x coordinate of bounding box
     * @param   maxY       upper right y coordinate of bounding box
     * @param   mapWidth   width of map image in px
     * @param   mapHeight  height of map image in px
     * @param   dpi        desired quality of scale image
     * @param   gridSize   grid size in m (all marks are set according to the grid size. For example, if a grid size of
     *                     of 1000 is specifed, a mark is set for each 1000m step on the map srting with the first
     *                     coordinate divisable by 1000)
     *
     * @return  image for vertical axis
     */
    public static Image generateVerticalAxisWithGrid(final double minX,
            final double minY,
            final double maxX,
            final double maxY,
            final int mapWidth,
            final int mapHeight,
            final int dpi,
            final int gridSize) {
        final double dpiScale = calculateDPIScale(dpi);

        final int imgWidth = (int)Math.floor(VERTICAL_AXIS_WIDTH * dpiScale);
        final int imgHeight = (int)Math.floor(mapHeight * dpiScale);

        final BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = configureImageGraphics(img, dpi);

        final List<Double> realWorldYCoords = findSuitableCoordinates(minY, maxY, gridSize);

        final double[] imgCoordinates = new double[realWorldYCoords.size()];
        for (int i = 0; i < imgCoordinates.length; i++) {
            imgCoordinates[i] = projectRealWorldCoordToImageCoord(realWorldYCoords.get(i).doubleValue(),
                    imgHeight,
                    minY,
                    maxY);
        }

        adjustAxisFontSize(g, realWorldYCoords, imgCoordinates);

        final FontMetrics metrics = g.getFontMetrics();

        // draw all coordinate marks
        double realWorldY;
        double imgY;
        String text;
        int textHeight;
        int textWidth;
        BufferedImage textImg;
        Graphics2D textG;
        double radianAngle;
        AffineTransform rotateTransform;
        int charWidth;
        int xTxtPos;
        int yTxtPos;
        Line2D line;
        for (int i = 0; i < imgCoordinates.length; i++) {
            realWorldY = realWorldYCoords.get(i).doubleValue();
            imgY = imgCoordinates[i];

            if (LOG.isDebugEnabled()) {
                LOG.debug("real world y: " + realWorldY + " img y: " + imgY);
            }

            // draw line marking y coordinate
            line = new Line2D.Double(0, imgHeight - imgY, imgWidth, imgHeight - imgY);
            g.draw(line);

            text = String.valueOf((int)realWorldY);
            textHeight = metrics.getHeight();
            textWidth = metrics.stringWidth(text);

            // draw text, if text does not intersect image boundaries
            if ((imgY + textWidth) < imgHeight) {
                // -- draw image with rotated text
                textImg = new BufferedImage(textHeight, textWidth, img.getType());

                textG = configureImageGraphics(textImg, dpi);
                textG.setFont(g.getFont());

                radianAngle = 270 * Math.PI / 180d;
                rotateTransform = new AffineTransform();
                rotateTransform.translate(0.5D * textHeight, 0.5D * textWidth);
                rotateTransform.rotate(radianAngle);
                rotateTransform.translate(-0.5D * textWidth, -0.5D * textHeight);
                textG.setTransform(rotateTransform);
                textG.drawString(text, 0, (int)(textHeight * 0.75d));
                textG.dispose();

                charWidth = metrics.charWidth(1);
                xTxtPos = (imgWidth / 2);
                yTxtPos = (int)(imgHeight - imgY - textWidth - charWidth);

                g.drawImage(textImg, xTxtPos, yTxtPos, null);
            }
        }

        g.dispose();

        return img;
    }

    /**
     * Adjusts the font size of the marks on the vertical or horizonal axis. The font size is adjusted, only if the mark
     * text does not fit into the gap between 2 marks.
     *
     * @param   g                current graphics context
     * @param   realWorldCoords  real world coordinates (needed to determine text width)
     * @param   imgCoords        image coordinates (mark positions)
     *
     * @return  true if the font size has been adjusted, false otherwise.
     */
    private static boolean adjustAxisFontSize(final Graphics2D g,
            final List<Double> realWorldCoords,
            final double[] imgCoords) {
        if (realWorldCoords.isEmpty()) {
            LOG.warn("list of real world coordinates is empty");
            return false;
        }

        final FontMetrics metrics = g.getFontMetrics();

        // as the last coordinate should be the biggest integer, we assume that it has also the biggest width
        final int maxStringWidth = metrics.stringWidth(realWorldCoords.get(realWorldCoords.size() - 1).toString());

        // determine gap between coordinate marks and scale font size accordingly
        if (imgCoords.length > 1) {
            final int distanceBetweenImgCoords = (int)Math.abs(imgCoords[1] - imgCoords[0]);
            return adjustFontSize(g, distanceBetweenImgCoords, maxStringWidth);
        }

        // there is either one or no mark -> scaling of font size is not needed
        return false;
    }

    /**
     * Generates image for marking coordinates on the horizontal axis. This method is needed because the PrintingWidget
     * passes the scale as String.<br/>
     * <b>NOTE:</b> The bounding box is supposed to be specified in a metric system such as EPSG:25832
     *
     * @param   minX       lower left x coordinate of bounding box
     * @param   minY       lower left y coordinate of bounding box
     * @param   maxX       upper right x coordinate of bounding box
     * @param   maxY       upper right y coordinate of bounding box
     * @param   mapWidth   width of map image in px
     * @param   mapHeight  height of map image in px
     * @param   dpi        desired quality of scale image
     * @param   scale      map scale in cm (the marks are set according to the grid derived from the scale). Note that
     *                     the passed String has to be parseable as Integer
     *
     * @return  image for horizontal axis
     */
    public static Image generateHorizontalAxis(final double minX,
            final double minY,
            final double maxX,
            final double maxY,
            final int mapWidth,
            final int mapHeight,
            final int dpi,
            final String scale) {
        return generateHorizontalAxis(minX, minY, maxX, maxY, mapWidth, mapHeight, dpi, Integer.parseInt(scale));
    }

    /**
     * Generates image for marking coordinates on the horizontal axis.<br/>
     * <b>NOTE:</b> The bounding box has to be specified in a metric system such as EPSG:25832
     *
     * @param   minX       lower left x coordinate of bounding box
     * @param   minY       lower left y coordinate of bounding box
     * @param   maxX       upper right x coordinate of bounding box
     * @param   maxY       upper right y coordinate of bounding box
     * @param   mapWidth   width of map image in px
     * @param   mapHeight  height of map image in px
     * @param   dpi        desired quality of scale image
     * @param   scale      map scale in cm (the marks are set according to the grid derived from the scale)
     *
     * @return  image for horizontal axis
     */
    public static Image generateHorizontalAxis(final double minX,
            final double minY,
            final double maxX,
            final double maxY,
            final int mapWidth,
            final int mapHeight,
            final int dpi,
            final int scale) {
        final int gridSize = getGridSize(scale);
        return generateHorizontalAxisWithGrid(minX, minY, maxX, maxY, mapWidth, mapHeight, dpi, gridSize);
    }

    /**
     * Generates image for marking coordinates on the horizontal axis.<br/>
     * <b>NOTE:</b> The bounding box has to be specified in a metric system such as EPSG:25832
     *
     * @param   minX       lower left x coordinate of bounding box
     * @param   minY       lower left y coordinate of bounding box
     * @param   maxX       upper right x coordinate of bounding box
     * @param   maxY       upper right y coordinate of bounding box
     * @param   mapWidth   width of map image in px
     * @param   mapHeight  height of map image in px
     * @param   dpi        desired quality of scale image
     * @param   gridSize   grid size in m (all marks are set according to the grid size. For example, if a grid size of
     *                     of 1000 is specifed, a mark is set for each 1000m step on the map srting with the first
     *                     coordinate divisable by 1000)
     *
     * @return  image for horizontal axis
     */
    public static Image generateHorizontalAxisWithGrid(final double minX,
            final double minY,
            final double maxX,
            final double maxY,
            final int mapWidth,
            final int mapHeight,
            final int dpi,
            final int gridSize) {
        final double dpiScale = calculateDPIScale(dpi);

        final int imgWidth = (int)Math.floor(mapWidth * dpiScale);
        final int imgHeight = (int)Math.floor(HORIZONTAL_AXIS_HEIGHT * dpiScale);

        final BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = configureImageGraphics(img, dpi);

        final List<Double> realWorldXCoords = findSuitableCoordinates(minX, maxX, gridSize);

        final double[] imgCoordinates = new double[realWorldXCoords.size()];
        for (int i = 0; i < imgCoordinates.length; i++) {
            imgCoordinates[i] = projectRealWorldCoordToImageCoord(realWorldXCoords.get(i).doubleValue(),
                    imgWidth,
                    minX,
                    maxX);
        }

        adjustAxisFontSize(g, realWorldXCoords, imgCoordinates);
        final FontMetrics metrics = g.getFontMetrics();

        double imgX;
        double realWorldX;
        Line2D line;
        String text;
        int charWidth;
        int xTxtPos;
        int yTxtPos;

        for (int i = 0; i < imgCoordinates.length; i++) {
            imgX = imgCoordinates[i];
            realWorldX = realWorldXCoords.get(i).doubleValue();

            if (LOG.isDebugEnabled()) {
                LOG.debug("real world x: " + realWorldX + " img x: " + imgX);
            }

            // draw line marking x coordinate
            line = new Line2D.Double(imgX, 0, imgX, imgHeight);
            g.draw(line);

            text = String.valueOf((int)realWorldX);

            if ((imgX + metrics.stringWidth(text)) < imgWidth) {
                charWidth = metrics.charWidth(1);
                xTxtPos = (int)(imgX + charWidth);
                yTxtPos = imgHeight / 2;

                g.drawString(text, xTxtPos, yTxtPos);
            }
        }

        g.dispose();

        return img;
    }
}
