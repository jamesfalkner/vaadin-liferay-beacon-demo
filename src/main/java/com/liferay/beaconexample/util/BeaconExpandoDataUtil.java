
package com.liferay.beaconexample.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portlet.expando.model.ExpandoRow;
import com.liferay.portlet.expando.model.ExpandoTable;
import com.liferay.portlet.expando.model.ExpandoValue;
import com.liferay.portlet.expando.service.ExpandoColumnLocalServiceUtil;
import com.liferay.portlet.expando.service.ExpandoRowLocalServiceUtil;
import com.liferay.portlet.expando.service.ExpandoTableLocalServiceUtil;
import com.liferay.portlet.expando.service.ExpandoValueLocalServiceUtil;
import com.vaadin.addon.charts.Chart;
import com.vaadin.addon.charts.model.Axis;
import com.vaadin.addon.charts.model.AxisType;
import com.vaadin.addon.charts.model.ChartType;
import com.vaadin.addon.charts.model.Configuration;
import com.vaadin.addon.charts.model.ContainerDataSeries;
import com.vaadin.addon.charts.model.Marker;
import com.vaadin.addon.charts.model.MarkerStates;
import com.vaadin.addon.charts.model.PlotOptionsArea;
import com.vaadin.addon.charts.model.PlotOptionsLine;
import com.vaadin.addon.charts.model.Series;
import com.vaadin.addon.charts.model.State;
import com.vaadin.addon.charts.model.States;
import com.vaadin.addon.charts.model.Title;
import com.vaadin.addon.charts.model.ZoomType;
import com.vaadin.data.Container;
import com.vaadin.data.Item;
import com.vaadin.data.util.IndexedContainer;

/**
 * Lots of static utility methods to get our data out of ExpandoTables.
 * 
 * @author James Falkner
 */
public class BeaconExpandoDataUtil {

	// Data point incremental period
	public static final long FIVE_MINUTES_IN_MILLIS = 5 * 60 * 1000;

	// IPC event constants and portlet session key constants
	public static final String BEACON_DATA_CLASS =
		"com.liferay.events.BeaconData";
	public static final String IPC_SELECTED_BUCKET =
		"com.liferay.beacons.selectedBucket";
	public static final String IPC_SELECTED_EVENT =
		"com.liferay.beacons.selectedEvent";
	public static final String IPC_SELECTED_REGION =
		"com.liferay.beacons.selectedRegion";
	public static final String IPC_REGION_CHART_DATA =
		"com.liferay.beacons.regionChartData";
	public static final String IPC_BEACON_CHART_DATA =
		"com.liferay.beacons.beaconChartData";
	public static final String IPC_REGION_FIRST_DATE =
		"com.liferay.beacons.regionFirstDate";

	// Expando Table constants
	public static final String BEACON_DATA_DATE_COL_NAME = "date";
	public static final int BEACON_DATA_DATE_COL_TYPE = 3;
	public static final String BEACON_DATA_BEACONS_COL_NAME = "beacons";
	public static final int BEACON_DATA_STR_COL_TYPE = 15;
	public static final String BEACON_DATA_REGIONS_COL_NAME = "regions";
	public static final String BEACON_DATA_ID_COL_NAME = "id";

	/**
	 * Get the first date for which beacon data was recorded
	 * @param event The event name
	 * @param companyId The company ID under which data can be found (in expando)
	 * @return The first recorded ping date for the event
	 * @throws PortalException if things go wrong
	 * @throws SystemException if things go wrong
	 */

	public static Date getFirstDateForEvent(String event, long companyId)
		throws PortalException, SystemException {

		List<ExpandoValue> dateStamps = getDateStampsForEvent(companyId, event);
		return (dateStamps.get(0).getDate());

	}

	/**
	 * Get the last date for which beacon data was recorded
	 * @param event The event name
	 * @param companyId The company ID under which data can be found (in expando)
	 * @return The last recorded ping date for the event
	 * @throws PortalException if things go wrong
	 * @throws SystemException if things go wrong
	 */
	public static Date getLastDateForEvent(String event, long companyId)
		throws PortalException, SystemException {

		List<ExpandoValue> dateStamps = getDateStampsForEvent(companyId, event);
		return (dateStamps.get(dateStamps.size() - 1).getDate());

	}

	/**
	 * Get a list of all events represented in the Expando tables
	 * @return a list of all events
	 * @throws SystemException if things go wrong
	 */
	public static List<String> getAllEvents()
		throws SystemException {

		List<String> allEvents = new ArrayList<String>();

		List<ExpandoTable> allTables =
			ExpandoTableLocalServiceUtil.getExpandoTables(-1, -1);
		for (ExpandoTable tbl : allTables) {
			if (!BeaconExpandoDataUtil.BEACON_DATA_CLASS.equals(tbl.getClassName())) {
				continue;
			}
			allEvents.add(tbl.getName());
		}
		return allEvents;
	}

	/**
	 * Generate Vaadin Containers for beacon region data.
	 * 
	 * @param event The name of the event for which to retrieve data
	 * @param companyId The company ID under which the data can be found
	 * @return A set of Vaadin Containers containing data for the event
	 * @throws PortalException if things go wrong
	 * @throws SystemException if things go wrong
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Container> getRegionChartDataForEvent(
		String event, long companyId)
		throws PortalException, SystemException {

		// Create the container set
		Map<String, Container> containers = new HashMap<String, Container>();

		List<ExpandoValue> dateStamps = getDateStampsForEvent(companyId, event);
		Date firstDate = dateStamps.get(0).getDate();
		long lastDate = dateStamps.get(dateStamps.size() - 1).getDate().getTime();
		long lastBucket = (lastDate - firstDate.getTime()) / FIVE_MINUTES_IN_MILLIS;

		Map<String, Map<Long, Set<String>>> regionPings =
			getRegionPingMap(companyId, event, dateStamps);

		Date now = new Date();
		for (String region : regionPings.keySet()) {

			Container container = new IndexedContainer();
			container.addContainerProperty("Number of Pings", Number.class, 0);
			container.addContainerProperty("Time of Day", Date.class, now);

			Map<Long, Set<String>> regionMap = regionPings.get(region);
			for (long i = 0; i < lastBucket; i++) {
				Set<String> allRegPings = regionMap.get(i);
				int count = allRegPings != null ? allRegPings.size() : 0;
				Item item = container.addItem(i);
				item.getItemProperty("Number of Pings").setValue(count);
				item.getItemProperty("Time of Day").setValue(
					new Date(firstDate.getTime() + (i * FIVE_MINUTES_IN_MILLIS)));
			}
			containers.put(region, container);
		}
		return containers;

	}

	/**
	 * Generate Vaadin Containers for beacon data.
	 * 
	 * @param event The name of the event for which to retrieve data
	 * @param companyId The company ID under which the data can be found
	 * @return A set of Vaadin Containers containing data for the event
	 * @throws PortalException if things go wrong
	 * @throws SystemException if things go wrong
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Map<String, Container>> getBeaconChartDataForBeacons(
		String event, long companyId)
		throws PortalException, SystemException {

		// Create the container set

		List<ExpandoValue> dateStamps = getDateStampsForEvent(companyId, event);

		Map<String, Map<String, Container>> result =
			new HashMap<String, Map<String, Container>>();

		Map<String, Map<String, Map<Long, Set<String>>>> beaconPings =
			getBeaconPingMap(companyId, event, dateStamps);

		for (String beaconName : beaconPings.keySet()) {

			Map<String, Container> containers = new HashMap<String, Container>();

			Date firstDate = dateStamps.get(0).getDate();
			long lastDate = dateStamps.get(dateStamps.size() - 1).getDate().getTime();
			long lastBucket =
				(lastDate - firstDate.getTime()) / FIVE_MINUTES_IN_MILLIS;

			Map<String, Map<Long, Set<String>>> beaconPingsForBeacon =
				beaconPings.get(beaconName);

			// [proximity, [TimeBucket, [id, id, id]]]

			Date now = new Date();
			for (String proximity : beaconPingsForBeacon.keySet()) {
				Container container = new IndexedContainer();
				container.addContainerProperty("Number of Pings", Number.class, 0);
				container.addContainerProperty("Time of Day", Date.class, now);

				Map<Long, Set<String>> proxPingMap =
					beaconPingsForBeacon.get(proximity);
				for (long i = 0; i < lastBucket; i++) {
					Set<String> allBeacPings = proxPingMap.get(i);
					int count = allBeacPings != null ? allBeacPings.size() : 0;
					Item item = container.addItem(i);
					item.getItemProperty("Number of Pings").setValue(count);
					item.getItemProperty("Time of Day").setValue(
						new Date(firstDate.getTime() + (i * FIVE_MINUTES_IN_MILLIS)));;
				}
				containers.put(proximity, container);
			}

			result.put(beaconName, containers);
		}

		return result;

	}

	/**
	 * Make a Vaadin Chart from a list of data series (represented as Vaadin containers)
	 * 
	 * @param event The name of the event
	 * @param data The set of data for this event
	 * @param firstDate The first date for which to create the chart
	 * @return A Vaadin chart showing all beacon region data for the specified event.
	 */

	public static Chart getRegionChartForEvent(
		Map<String, Container> data, Date firstDate) {

		Chart chart = new Chart();
		chart.setHeight("100%");
		chart.setWidth("100%");

		Configuration configuration = new Configuration();
		configuration.getChart().setZoomType(ZoomType.X);
		configuration.getChart().setType(ChartType.LINE);
		configuration.getTitle().setText("iBeacon Region Pings");

		String title = "Click and drag in the plot area to zoom in";
		configuration.getSubTitle().setText(title);

		configuration.getxAxis().setType(AxisType.DATETIME);
		configuration.getxAxis().setMinRange(FIVE_MINUTES_IN_MILLIS);
		configuration.getxAxis().setTitle(new Title("Time of Day"));

		configuration.getLegend().setEnabled(true);

		Axis yAxis = configuration.getyAxis();
		yAxis.setTitle(new Title("Number of Pings"));
		yAxis.setStartOnTick(false);
		yAxis.setShowFirstLabel(false);

		configuration.getTooltip().setShared(true);

		PlotOptionsLine plotOptions = new PlotOptionsLine();

		plotOptions.setLineWidth(1);
		plotOptions.setShadow(false);

		plotOptions.setPointStart(firstDate);
		plotOptions.setPointInterval(FIVE_MINUTES_IN_MILLIS);

		Marker marker = new Marker();
		marker.setEnabled(false);
		State hoverState = new State(true);
		hoverState.setRadius(5);
		MarkerStates states = new MarkerStates(hoverState);
		marker.setStates(states);

		State hoverStateForArea = new State(true);
		hoverState.setLineWidth(1);

		plotOptions.setStates(new States(hoverStateForArea));
		plotOptions.setMarker(marker);
		plotOptions.setShadow(true);
		configuration.setPlotOptions(plotOptions);

		List<Series> containerDataSeries = new ArrayList<Series>();
		for (String seriesName : data.keySet()) {
			ContainerDataSeries series =
				new ContainerDataSeries(data.get(seriesName));

			series.setName(seriesName);
			series.setYPropertyId("Number of Pings");
			containerDataSeries.add(series);
		}

		configuration.setSeries(containerDataSeries);
		chart.drawChart(configuration);

		return chart;
	}

	/**
	 * Make a Vaadin Chart from a list of data series (represented as Vaadin containers)
	 * 
	 * @param event The name of the event
	 * @param beaconName The name of a specific beacon
	 * @param data The set of data for this beacon
	 * @param firstDate The first date for which to create the chart
	 * @return A Vaadin chart showing all beacon proximity data for the specified beacon.
	 */
	public static Chart getBeaconProximityChartForEvent(
		String event, String beaconName, Map<String, Container> data, Date firstDate) {

		Chart chart = new Chart();
		chart.setHeight("100%");
		chart.setWidth("100%");

		Configuration configuration = new Configuration();
		configuration.getChart().setZoomType(ZoomType.X);
		configuration.getChart().setType(ChartType.AREA);
		configuration.getTitle().setText(
			event + ": Individual Beacon Proximity Pings for " + beaconName);

		String title = "Click and drag in the plot area to zoom in";
		configuration.getSubTitle().setText(title);

		configuration.getxAxis().setType(AxisType.DATETIME);
		configuration.getxAxis().setMinRange(FIVE_MINUTES_IN_MILLIS);
		configuration.getxAxis().setTitle(new Title("Time of Day"));

		configuration.getLegend().setEnabled(true);

		Axis yAxis = configuration.getyAxis();
		yAxis.setTitle(new Title("Number of Pings"));
		yAxis.setStartOnTick(false);
		yAxis.setShowFirstLabel(false);

		configuration.getTooltip().setShared(true);

		PlotOptionsArea plotOptions = new PlotOptionsArea();

		plotOptions.setLineWidth(2);
		plotOptions.setShadow(true);

		plotOptions.setPointStart(firstDate);
		plotOptions.setPointInterval(FIVE_MINUTES_IN_MILLIS);

		Marker marker = new Marker();
		marker.setEnabled(false);
		State hoverState = new State(true);
		hoverState.setRadius(5);
		MarkerStates states = new MarkerStates(hoverState);
		marker.setStates(states);

		State hoverStateForArea = new State(true);
		hoverState.setLineWidth(1);

		plotOptions.setStates(new States(hoverStateForArea));
		plotOptions.setMarker(marker);
		plotOptions.setShadow(true);
		configuration.setPlotOptions(plotOptions);

		List<Series> containerDataSeries = new ArrayList<Series>();
		for (String proxName : data.keySet()) {
			ContainerDataSeries series = new ContainerDataSeries(data.get(proxName));
			series.setName(proxName);
			series.setYPropertyId("Number of Pings");
			containerDataSeries.add(series);
		}

		configuration.setSeries(containerDataSeries);
		chart.drawChart(configuration);

		return chart;
	}

	/**
	 * Get all the date stamps for a given event
	 * @param companyId the company ID in which the data exists (in Expando)
	 * @param event Name of event
	 * @return A list of all date stamps, sorted chronologically
	 * @throws PortalException if things go wrong
	 * @throws SystemException if things go wrong
	 */
	private static List<ExpandoValue> getDateStampsForEvent(
		long companyId, String event)
		throws PortalException, SystemException {

		List<ExpandoValue> dateStampOrig =
			ExpandoValueLocalServiceUtil.getColumnValues(
				companyId, BEACON_DATA_CLASS, event, BEACON_DATA_DATE_COL_NAME, -1, -1);

		List<ExpandoValue> dateStamps = new ArrayList<ExpandoValue>();
		dateStamps.addAll(dateStampOrig);

		Collections.sort(dateStamps, new Comparator<ExpandoValue>() {

			@Override
			public int compare(ExpandoValue ev1, ExpandoValue ev2) {

				try {
					Date d1 = ev1.getDate();
					Date d2 = ev2.getDate();
					return d1.compareTo(d2);
				}
				catch (Exception ex) {
					return 0;
				}
			}
		});
		return dateStamps;
	}

	/**
	 * Get a raw representation of the beacon ping data, un-JSONified
	 * 
	 * @param companyId The companyID
	 * @param event name of the event for which data is desired
	 * @param dateStamps The dates of all pings to retrieve
	 * @return A hierarchical map of pings based on dates
	 * @throws PortalException if things go wrong
	 * @throws SystemException if things go wrong
	 */
	private static Map<String, Map<Long, Set<String>>> getRegionPingMap(
		long companyId, String event, List<ExpandoValue> dateStamps)
		throws PortalException, SystemException {

		ExpandoTable table =
			ExpandoTableLocalServiceUtil.getTable(companyId, BEACON_DATA_CLASS, event);

		long firstDate = dateStamps.get(0).getDate().getTime();

		// [RegionName, [TimeBucket, [id, id, id, id]]]
		Map<String, Map<Long, Set<String>>> regionPings =
			new HashMap<String, Map<Long, Set<String>>>();
		List<ExpandoRow> allRows =
			ExpandoRowLocalServiceUtil.getRows(
				companyId, BEACON_DATA_CLASS, event, -1, -1);

		for (ExpandoRow row : allRows) {

			Date rowDate =
				ExpandoValueLocalServiceUtil.getValue(
					companyId, BEACON_DATA_CLASS, table.getName(), "date",
					row.getClassPK()).getDate();
			String rowId =
				ExpandoValueLocalServiceUtil.getValue(
					companyId, BEACON_DATA_CLASS, table.getName(), "id", row.getClassPK()).getString();
			String rowRegions =
				ExpandoValueLocalServiceUtil.getValue(
					companyId, BEACON_DATA_CLASS, table.getName(), "regions",
					row.getClassPK()).getString();
			Long timeBucket =
				(rowDate.getTime() - firstDate) / FIVE_MINUTES_IN_MILLIS;
			JSONArray regs = JSONFactoryUtil.createJSONArray(rowRegions);
			for (int i = 0; i < regs.length(); i++) {
				String reg = regs.getString(i).trim();
				if (Validator.isNull(reg))
					continue;
				Map<Long, Set<String>> pings = regionPings.get(reg);
				if (pings != null) {
					Set<String> pingStrs = pings.get(timeBucket);
					if (pingStrs != null) {
						pingStrs.add(rowId);
					}
					else {
						pingStrs = new HashSet<String>();
						pingStrs.add(rowId);
						pings.put(timeBucket, pingStrs);
					}
				}
				else {
					pings = new HashMap<Long, Set<String>>();
					Set<String> newPingStrs = new HashSet<String>();
					newPingStrs.add(rowId);
					pings.put(timeBucket, newPingStrs);
					regionPings.put(reg, pings);
				}
			}

		}
		return regionPings;
	}

	/**
	 * Get a raw representation of the beacon region ping data, un-JSONified
	 * 
	 * @param companyId The companyID
	 * @param event name of the event for which data is desired
	 * @param dateStamps The dates of all pings to retrieve
	 * @return A hierarchical map of pings based on dates
	 * @throws PortalException if things go wrong
	 * @throws SystemException if things go wrong
	 */
	private static Map<String, Map<String, Map<Long, Set<String>>>> getBeaconPingMap(
		long companyId, String event, List<ExpandoValue> dateStamps)
		throws PortalException, SystemException {

		ExpandoTable table =
			ExpandoTableLocalServiceUtil.getTable(companyId, BEACON_DATA_CLASS, event);

		long firstDate = dateStamps.get(0).getDate().getTime();

		// [BeaconName, [proximity, [TimeBucket, [id, id, id]]]]
		Map<String, Map<String, Map<Long, Set<String>>>> beaconPings =
			new HashMap<String, Map<String, Map<Long, Set<String>>>>();

		List<ExpandoRow> allRows =
			ExpandoRowLocalServiceUtil.getRows(
				companyId, BEACON_DATA_CLASS, event, -1, -1);

		for (ExpandoRow row : allRows) {

			Date rowDate =
				ExpandoValueLocalServiceUtil.getValue(
					companyId, BEACON_DATA_CLASS, table.getName(), BEACON_DATA_DATE_COL_NAME,
					row.getClassPK()).getDate();
			String rowId =
				ExpandoValueLocalServiceUtil.getValue(
					companyId, BEACON_DATA_CLASS, table.getName(), BEACON_DATA_ID_COL_NAME, row.getClassPK()).getString();
			String rowBeacons =
				ExpandoValueLocalServiceUtil.getValue(
					companyId, BEACON_DATA_CLASS, table.getName(), BEACON_DATA_BEACONS_COL_NAME,
					row.getClassPK()).getString();
			Long timeBucket =
				(rowDate.getTime() - firstDate) / FIVE_MINUTES_IN_MILLIS;
			JSONArray beacs = JSONFactoryUtil.createJSONArray(rowBeacons);

			for (int i = 0; i < beacs.length(); i++) {
				JSONObject beacPings = beacs.getJSONObject(i);
				String beacName = beacPings.getString("beacon_name");
				String beacProx = beacPings.getString("proximity");
				Map<String, Map<Long, Set<String>>> beacMap = beaconPings.get(beacName);
				if (beacMap != null) {
					Map<Long, Set<String>> beacTimes = beacMap.get(beacProx);
					if (beacTimes != null) {
						Set<String> beacProxs = beacTimes.get(timeBucket);
						if (beacProxs != null) {
							beacProxs.add(rowId);
						}
						else {
							beacProxs = new HashSet<String>();
							beacProxs.add(rowId);
							beacTimes.put(timeBucket, beacProxs);

						}
					}
					else {
						beacTimes = new HashMap<Long, Set<String>>();
						Set<String> newSet = new HashSet<String>();
						newSet.add(rowId);
						beacTimes.put(timeBucket, newSet);
						beacMap.put(beacProx, beacTimes);
					}
				}
				else {
					beacMap = new HashMap<String, Map<Long, Set<String>>>();
					Map<Long, Set<String>> newBeacs = new HashMap<Long, Set<String>>();
					Set<String> newSet = new HashSet<String>();
					newSet.add(rowId);
					newBeacs.put(timeBucket, newSet);
					beacMap.put(beacProx, newBeacs);
					beaconPings.put(beacName, beacMap);
				}
			}
		}

		return beaconPings;
	}

	/**
	 * Clear the Beacon Expando data tables we use
	 * @param companyId the ID of the company (duh)
	 * @throws Exception if things go wrong
	 */
	public static void clearFakeData(long companyId)
		throws Exception {

		List<ExpandoTable> allTables =
			ExpandoTableLocalServiceUtil.getExpandoTables(-1, -1);

		List<String> allCurrentEvents = new ArrayList<String>();
		for (ExpandoTable tbl : allTables) {
			if (!BEACON_DATA_CLASS.equals(tbl.getClassName())) {
				continue;
			}

			String name = tbl.getName();
			allCurrentEvents.add(name);
		}

		for (String fakeEvent : allCurrentEvents) {

			ExpandoTable tbl =
				ExpandoTableLocalServiceUtil.getTable(
					companyId, BEACON_DATA_CLASS, fakeEvent);

			ExpandoValueLocalServiceUtil.deleteTableValues(tbl.getTableId());

			List<ExpandoRow> rows =
				ExpandoRowLocalServiceUtil.getRows(
					companyId, BEACON_DATA_CLASS, fakeEvent, -1, -1);
			for (ExpandoRow row : rows) {
				ExpandoRowLocalServiceUtil.deleteRow(row.getRowId());
			}
			ExpandoTableLocalServiceUtil.deleteTable(tbl.getTableId());

		}

	}

	/**
	 * Make a bunch of fake, hard-coded demo data
	 * 
	 * @param companyId
	 *          The company ID in which the data should be made
	 * @throws Exception
	 *           if things go wrong.
	 */
	public static void makeFakeData(long companyId)
		throws Exception {

		clearFakeData(companyId);

		String[] fakeEvents =
			new String[] {
				"Benelux Solutions Forum", "France Symposium",
				"North America Symposium", "DevCon Frankfurt"
			};

		for (String fakeEvent : fakeEvents) {

			ExpandoTable fakeTable =
				ExpandoTableLocalServiceUtil.addTable(
					companyId, BEACON_DATA_CLASS, fakeEvent);
			ExpandoColumnLocalServiceUtil.addColumn(
				fakeTable.getTableId(), BEACON_DATA_DATE_COL_NAME,
				BEACON_DATA_DATE_COL_TYPE);
			ExpandoColumnLocalServiceUtil.addColumn(
				fakeTable.getTableId(), BEACON_DATA_BEACONS_COL_NAME,
				BEACON_DATA_STR_COL_TYPE);
			ExpandoColumnLocalServiceUtil.addColumn(
				fakeTable.getTableId(), BEACON_DATA_REGIONS_COL_NAME,
				BEACON_DATA_STR_COL_TYPE);
			ExpandoColumnLocalServiceUtil.addColumn(
				fakeTable.getTableId(), BEACON_DATA_ID_COL_NAME,
				BEACON_DATA_STR_COL_TYPE);

			Date now = new Date();
			String[] proximities = new String[] {
				"near", "far", "immediate"
			};
			String[] regions = new String[] {
				"Venue", "Registration", "Partners", "Grand Ballroom", "Bar"
			};
			String[] beacons =
				new String[] {
					"Componence", "GFI", "Smile", "iProfs", "SQLI", "CGI", "ORANGE",
					"Mystery Guest"
				};

			for (int i = 0; i < 70; i++) {
				int people = (int) Math.floor(Math.random() * 100);
				if (i > 23 && i < 30)
					people = (int) ((double) people * 4);
				if (i > 50 && i < 60)
					people = (int) ((double) people * 3);
				if (i > 40 && i < 42)
					people = (int) ((double) people * 2);
				if (i > 60 && i < 64)
					people = (int) ((double) people * 3);
				for (int p = 0; p < people; p++) {
					int numRegions =
						(int) Math.floor(Math.random() * (regions.length + 1));
					if (i == 23 || i == 70) {
						numRegions += 5;
					}
					int numBeacons =
						(int) Math.floor(Math.random() * (beacons.length + 1));
					String id = Double.toString(Math.random());
					long newTime = now.getTime() + (i * (5 * 60 * 1000));
					Date newDate = new Date(newTime);
					long classPK = (fakeEvent + id + newDate.getTime()).hashCode();
					JSONArray regArr = JSONFactoryUtil.createJSONArray();
					JSONArray beacArr = JSONFactoryUtil.createJSONArray();
					for (int r = 0; r < numRegions; r++) {
						regArr.put(regions[(int) (Math.random() * regions.length)]);
					}
					if (i < 15) {
						for (int e = 0; e < 30; e++) {
							regArr.put(regions[3]);
						}
					}
					if (i > 40 && i < 55) {
						for (int e = 0; e < 250; e++) {
							regArr.put(regions[2]);
						}
					}

					for (int r = 0; r < numBeacons; r++) {
						JSONObject obj = JSONFactoryUtil.createJSONObject();
						obj.put(
							"beacon_name", beacons[(int) (Math.random() * beacons.length)]);
						obj.put(
							"proximity",
							proximities[(int) (Math.random() * proximities.length)]);
						beacArr.put(obj);
					}
					ExpandoValueLocalServiceUtil.addValue(
						companyId, BEACON_DATA_CLASS, fakeEvent, BEACON_DATA_DATE_COL_NAME,
						classPK, newDate);
					ExpandoValueLocalServiceUtil.addValue(
						companyId, BEACON_DATA_CLASS, fakeEvent, BEACON_DATA_ID_COL_NAME,
						classPK, id);
					ExpandoValueLocalServiceUtil.addValue(
						companyId, BEACON_DATA_CLASS, fakeEvent,
						BEACON_DATA_BEACONS_COL_NAME, classPK, beacArr.toString());
					ExpandoValueLocalServiceUtil.addValue(
						companyId, BEACON_DATA_CLASS, fakeEvent,
						BEACON_DATA_REGIONS_COL_NAME, classPK, regArr.toString());
				}
			}
		}

	}
}
