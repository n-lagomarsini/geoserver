package org.geoserver.coverage;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;

import net.opengis.wcs20.DimensionSubsetType;
import net.opengis.wcs20.GetCoverageType;
import net.opengis.wcs20.Wcs20Factory;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.eclipse.emf.common.util.EList;
import org.geoserver.wcs2_0.WebCoverageService20;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.TileResponseReceiver;
import org.geowebcache.mime.ErrorMime;
import org.geowebcache.mime.XMLMime;
import org.geowebcache.service.ServiceException;
import org.geowebcache.util.GWCVars;
import org.geowebcache.util.ServletUtils;
import org.springframework.util.Assert;

/**
 * Builds WCS requests to gather a certain tile or meta tile. The actual communication with the
 * server is delegated to subclasses (might be a real HTTP request, but also an in process one in
 * the case of GeoServer)
 */
public abstract class WCSSourceHelper {

    private static final Wcs20Factory WCS20_FACTORY = Wcs20Factory.eINSTANCE;

    WebCoverageService20 wcsService;

    private int concurrency = 32;
    private int backendTimetout;

    public void makeRequest(WCSMetaTile metaTile, Resource target) throws GeoWebCacheException {

        Map<String, String> wcsParams= null;// metaTile.getwcsParams();
        WCSLayer layer = metaTile.getLayer();
        String format = metaTile.getRequestFormat().getFormat();

        makeRequest(metaTile, layer, wcsParams, format, target);
    }

    public void makeRequest(ConveyorTile tile, Resource target) throws GeoWebCacheException {
        WCSLayer layer = (WCSLayer) tile.getLayer();

        GridSubset gridSubset = layer.getGridSubset(tile.getGridSetId());

        Map<String, String> wcsParams= null;
//        layer.getWCSRequestTemplate(tile.getMimeType(),
//                null/*WCSLayer.RequestType.MAP*/);

        GetCoverageType request = setupGetCoverageRequest();
        wcsService.getCoverage(request);
        wcsParams.put("FORMAT", tile.getMimeType().getMimeType());
//        wcsParams.put("SRS", layer.backendSRSOverride(gridSubset.getSRS()));
        wcsParams.put("HEIGHT", String.valueOf(gridSubset.getTileHeight()));
        wcsParams.put("WIDTH", String.valueOf(gridSubset.getTileWidth()));
        // strBuilder.append("&TILED=").append(requestTiled);

        BoundingBox bbox = gridSubset.boundsFromIndex(tile.getTileIndex());

        wcsParams.put("BBOX", bbox.toString());

        Map<String, String> fullParameters = tile.getFullParameters();
        if (fullParameters.isEmpty()) {
            fullParameters = layer.getDefaultParameterFilters();
        }
        wcsParams.putAll(fullParameters);

        if (tile.getMimeType() == XMLMime.kml) {
            // This is a hack for GeoServer to produce regionated KML,
            // but it is unlikely to do much harm, especially since nobody
            // else appears to produce regionated KML at this point
            wcsParams.put("format_options", "mode:superoverlay;overlaymode:auto");
        }

        String mimeType = tile.getMimeType().getMimeType();
        makeRequest(tile, layer, wcsParams, mimeType, target);
    }

    private GetCoverageType setupGetCoverageRequest() {
        GetCoverageType getCoverage = WCS20_FACTORY.createGetCoverageType();
        EList<DimensionSubsetType> dimensionSubset = getCoverage.getDimensionSubset();
        return getCoverage;
}

    protected boolean mimeStringCheck(String requestMime, String responseMime) {
        if (responseMime.equalsIgnoreCase(requestMime)) {
            return true;
        } else if (responseMime.startsWith(requestMime)) {
            return true;
        } else if (requestMime.startsWith("image/png") && responseMime.startsWith("image/png")) {
            return true;
        }
        return false;
    }

    /**
     * The levels of concurrent requests this source helper is allowing
     * @return
     */
    public int getConcurrency() {
        return concurrency;
    }

    /**
     * Sets the maximum amount of concurrent requests this source helper will issue
     * @param concurrency
     */
    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    /**
     * Sets the backend timeout for HTTP calls
     * @param backendTimeout
     */
    public void setBackendTimeout(int backendTimeout) {
        this.backendTimetout = backendTimeout;
    }
    
    /**
     * Returns the backend timeout for HTTP calls
     */
    public int getBackendTimeout() {
        return this.backendTimetout;
    }
    
    
    
    protected void makeRequest(TileResponseReceiver tileRespRecv, WCSLayer layer,
            Map<String, String> wcsParams, String expectedMimeType, Resource target)
            throws GeoWebCacheException {
        Assert.notNull(target, "Target resource can't be null");
        Assert.isTrue(target.getSize() == 0, "Target resource is not empty");

        URL wmsBackendUrl = null;

        final Integer backendTimeout = layer.getBackendTimeout();
        int backendTries = 0; // keep track of how many backends we have tried
        GeoWebCacheException fetchException = null;
//        while (target.getSize() == 0 && backendTries < layer.getWMSurl().length) {
//            String requestUrl = layer.nextWmsURL();
//
//            try {
//                wmsBackendUrl = new URL(requestUrl);
//            } catch (MalformedURLException maue) {
//                throw new GeoWebCacheException("Malformed URL: " + requestUrl + " "
//                        + maue.getMessage());
//            }
//            try {
//                connectAndCheckHeaders(tileRespRecv, wmsBackendUrl, wcsParams, expectedMimeType,
//                        backendTimeout, target);
//            } catch (GeoWebCacheException e) {
//                fetchException = e;
//            }
//
//            backendTries++;
//        }

        if (target.getSize() == 0) {
            String msg = "All backends (" + backendTries + ") failed.";
            if (fetchException != null) {
                msg += " Reason: " + fetchException.getMessage() + ". ";
            }
            msg += " Last request: '"
                    + wmsBackendUrl.toString()
                    + "'. "
                    + (tileRespRecv.getErrorMessage() == null ? "" : tileRespRecv.getErrorMessage());

            tileRespRecv.setError();
            tileRespRecv.setErrorMessage(msg);
            throw new GeoWebCacheException(msg);
        }
    }

    /**
     * Executes the actual HTTP request, checks the response headers (status and MIME) and
     * 
     * @param tileRespRecv
     * @param wmsBackendUrl
     * @param data
     * @param wmsparams
     * @return
     * @throws GeoWebCacheException
     */
    private void connectAndCheckHeaders(TileResponseReceiver tileRespRecv, URL wmsBackendUrl,
            Map<String, String> wmsParams, String requestMime, Integer backendTimeout,
            Resource target) throws GeoWebCacheException {
//
//        GetMethod getMethod = null;
//        final int responseCode;
//        final int responseLength;
//
//        try { // finally
//            try {
//                getMethod = executeRequest(wmsBackendUrl, wmsParams, backendTimeout);
//                responseCode = getMethod.getStatusCode();
//                responseLength = (int) getMethod.getResponseContentLength();
//
//                // Do not set error at this stage
//            } catch (IOException ce) {
//                if (log.isDebugEnabled()) {
//                    String message = "Error forwarding request " + wmsBackendUrl.toString();
//                    log.debug(message, ce);
//                }
//                throw new GeoWebCacheException(ce);
//            }
//            // Check that the response code is okay
//            tileRespRecv.setStatus(responseCode);
//            if (responseCode != 200 && responseCode != 204) {
//                tileRespRecv.setError();
//                throw new ServiceException("Unexpected response code from backend: " + responseCode
//                        + " for " + wmsBackendUrl.toString());
//            }
//
//            // Check that we're not getting an error MIME back.
//            String responseMime = getMethod.getResponseHeader("Content-Type").getValue();
//            if (responseCode != 204 && responseMime != null
//                    && !mimeStringCheck(requestMime, responseMime)) {
//                String message = null;
//                if (responseMime.equalsIgnoreCase(ErrorMime.vnd_ogc_se_inimage.getFormat())) {
//                    // TODO: revisit: I don't understand why it's trying to create a String message
//                    // out of an ogc_se_inimage response?
//                    InputStream stream = null;
//                    try {
//                        stream = getMethod.getResponseBodyAsStream();
//                        byte[] error = IOUtils.toByteArray(stream);
//                        message = new String(error);
//                    } catch (IOException ioe) {
//                        // Do nothing
//                    } finally {
//                        IOUtils.closeQuietly(stream);
//                    }
//                } else if (responseMime != null
//                        && responseMime.toLowerCase().startsWith("application/vnd.ogc.se_xml")) {
//                    InputStream stream = null;
//                    try {
//                        stream = getMethod.getResponseBodyAsStream();
//                        message = IOUtils.toString(stream);
//                    } catch (IOException e) {
//                        //
//                    } finally {
//                        IOUtils.closeQuietly(stream);
//                    }
//                }
//                String msg = "MimeType mismatch, expected " + requestMime + " but got "
//                        + responseMime + " from " + wmsBackendUrl.toString()
//                        + (message == null ? "" : (":\n" + message));
//                tileRespRecv.setError();
//                tileRespRecv.setErrorMessage(msg);
//                log.warn(msg);
//            }
//
//            // Everything looks okay, try to save expiration
//            if (tileRespRecv.getExpiresHeader() == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
//                String expireValue = getMethod.getResponseHeader("Expires").getValue();
//                long expire = ServletUtils.parseExpiresHeader(expireValue);
//                if (expire != -1) {
//                    tileRespRecv.setExpiresHeader(expire / 1000);
//                }
//            }
//
//            // Read the actual data
//            if (responseCode != 204) {
//                try {
//                    InputStream inStream = getMethod.getResponseBodyAsStream();
//                    if( inStream == null ){
//                        String uri = getMethod.getURI().getURI();
//                        log.error( "No response for "+getMethod.getName() +" " + uri );
//                    }
//                    else {
//                            ReadableByteChannel channel = Channels.newChannel(inStream);
//                            try {
//                                target.transferFrom(channel);
//                            } finally {
//                                channel.close();
//                            }
//                    }
//                    if (responseLength > 0) {
//                        int readAccu = (int) target.getSize();
//                        if (readAccu != responseLength) {
//                            tileRespRecv.setError();
//                            throw new GeoWebCacheException("Responseheader advertised "
//                                    + responseLength + " bytes, but only received " + readAccu
//                                    + " from " + wmsBackendUrl.toString());
//                        }
//                    }
//                } catch (IOException ioe) {
//                    tileRespRecv.setError();
//                    log.error("Caught IO exception, " + wmsBackendUrl.toString() + " "
//                            + ioe.getMessage());
//                }
//            }
//
//        } finally {
//            if (getMethod != null) {
//                getMethod.releaseConnection();
//            }
//        }
    }

//    /**
//     * sets up a HTTP GET request to a URL and configures authentication.
//     * 
//     * @param url
//     *            endpoint to talk to
//     * @param queryParams
//     *            parameters for the query string
//     * @param backendTimeout
//     *            timeout to use in seconds
//     * @return executed GetMethod (that has to be closed after reading the response!)
//     * @throws HttpException
//     * @throws IOException
//     */
//    public GetMethod executeRequest(final URL url, final Map<String, String> queryParams,
//            final Integer backendTimeout) throws HttpException, IOException {
//        // grab the client
//        HttpClient httpClient = getHttpClient();
//        
//        // prepare the request
//        GetMethod getMethod = new GetMethod(url.toString());
//        if (queryParams != null && queryParams.size() > 0) {
//            NameValuePair[] params = new NameValuePair[queryParams.size()];
//            int i = 0;
//            for (Map.Entry<String, String> e : queryParams.entrySet()) {
//                params[i] = new NameValuePair(e.getKey(), e.getValue());
//                i++;
//            }
//            getMethod.setQueryString(params);
//        }
//        getMethod.setDoAuthentication(doAuthentication);
//
//        // fire!
//        if (log.isDebugEnabled()) {
//                log.trace( getMethod.getURI().getURI() );
//        }
//        httpClient.executeMethod(getMethod);
//        return getMethod;
//    }


}
