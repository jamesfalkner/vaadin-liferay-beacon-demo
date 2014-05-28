
package com.liferay.beaconexample;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.portlet.PortletRequest;
import javax.portlet.PortletSession;

import com.liferay.beaconexample.util.BeaconExpandoDataUtil;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.util.PortalUtil;
import com.vaadin.addon.charts.Chart;
import com.vaadin.addon.charts.PointClickEvent;
import com.vaadin.addon.charts.PointClickListener;
import com.vaadin.addon.ipcforliferay.LiferayIPC;
import com.vaadin.addon.ipcforliferay.event.LiferayIPCEvent;
import com.vaadin.addon.ipcforliferay.event.LiferayIPCEventListener;
import com.vaadin.annotations.PreserveOnRefresh;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Widgetset;
import com.vaadin.data.Container;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.server.VaadinPortletService;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.NativeSelect;
import com.vaadin.ui.Notification;
import com.vaadin.ui.ProgressBar;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

/**
 * This Vaadin portlet visualizes data from Expando Liferay tables. It will render
 * time-series data based on the selection made by the user, and also communicate
 * with other portlets via Liferay IPC addon.
 * 
 * @author James Falkner
 *
 */
@SuppressWarnings({
	"serial"
})
@Theme("liferay")
@PreserveOnRefresh
@Widgetset("com.liferay.mavenizedbeacons.AppWidgetSet")
public class BeaconRegionGraph extends UI implements Serializable {

	private VerticalLayout layout = new VerticalLayout();
	private HorizontalLayout chartLayout = new HorizontalLayout();
	private LiferayIPC ipc;
	private long companyId;
	private final NativeSelect ls = new NativeSelect("Event");
	
	@Override
	protected void init(VaadinRequest request) {

		// get some liferay stuff
		PortletRequest req = VaadinPortletService.getCurrentPortletRequest();
		companyId = PortalUtil.getCompanyId(req);

		ipc = new LiferayIPC();
		ipc.extend(this);

		// build ui
		List<String> allEvents;
		try {
			allEvents = BeaconExpandoDataUtil.getAllEvents();
		}
		catch (SystemException e) {
			Notification.show(
				"Error", e.getLocalizedMessage(), Notification.Type.WARNING_MESSAGE);
			return;
		}

		layout.addComponent(ls);
		layout.addComponent(chartLayout);

		if (Validator.isNotNull(allEvents) && allEvents.size() > 0) {
			ls.addItems(allEvents.toArray());
		}
		
		// add listener to show new graph
		ls.addValueChangeListener(new ValueChangeListener() {

				@Override
				public void valueChange(ValueChangeEvent evt) {

					if (ls.getValue() == null) {
						return;
					}

					String event = ls.getValue().toString();
					Map<String, Container> regionChartData;
					Map<String, Map<String, Container>> beaconChartData;
					final Date firstDate;
					final Date lastDate;

					try {
						regionChartData =
							BeaconExpandoDataUtil.getRegionChartDataForEvent(event, companyId);
						beaconChartData =
							BeaconExpandoDataUtil.getBeaconChartDataForBeacons(
								event, companyId);
						firstDate =
							BeaconExpandoDataUtil.getFirstDateForEvent(event, companyId);
						lastDate =
							BeaconExpandoDataUtil.getLastDateForEvent(event, companyId);

						// set big data into session for use by other portlets
						PortletSession ps = VaadinPortletService.getCurrentPortletRequest()
								.getPortletSession();

						ps.setAttribute(
							BeaconExpandoDataUtil.IPC_REGION_CHART_DATA, regionChartData,
							PortletSession.APPLICATION_SCOPE);
						ps.setAttribute(
							BeaconExpandoDataUtil.IPC_REGION_FIRST_DATE, firstDate,
							PortletSession.APPLICATION_SCOPE);
						ps.setAttribute(
							BeaconExpandoDataUtil.IPC_BEACON_CHART_DATA, beaconChartData,
							PortletSession.APPLICATION_SCOPE);

						ipc.sendEvent(BeaconExpandoDataUtil.IPC_SELECTED_EVENT, event);

					}
					catch (Exception e) {
						Notification.show(
							"Error", e.getLocalizedMessage(),
							Notification.Type.WARNING_MESSAGE);
						e.printStackTrace();
						return;
					}

					// Now make the Vaadin Chart

					final Chart chart =
						BeaconExpandoDataUtil.getRegionChartForEvent(
							regionChartData, firstDate);

					chart.addPointClickListener(new PointClickListener() {

						@Override
						public void onClick(PointClickEvent evt) {

							ipc.sendEvent(
								BeaconExpandoDataUtil.IPC_SELECTED_REGION,
								evt.getSeries().getName());
						}
					});

					chartLayout.removeAllComponents();
					chartLayout.addComponent(chart);
					chartLayout.setExpandRatio(chart, 1);

					ipc.addLiferayIPCEventListener(
						BeaconExpandoDataUtil.IPC_SELECTED_BUCKET,
						new LiferayIPCEventListener() {

							@Override
							public void eventReceived(LiferayIPCEvent event) {

								if (Validator.isNull(event.getData())) {
									/* reset chart to original extents */
									chart.getConfiguration().getxAxis().setExtremes(
										firstDate.getTime(), lastDate.getTime());
								}
								else {
									/* zoom in +/- 3 time periods */
									long itemClicked = Long.parseLong(event.getData());
									long before = itemClicked > 3 ? itemClicked - 3 : 0;
									long after = before + 6;
									chart.getConfiguration().getxAxis().setMin(
										new Date(firstDate.getTime() + before *
											BeaconExpandoDataUtil.FIVE_MINUTES_IN_MILLIS));
									chart.getConfiguration().getxAxis().setMax(
										new Date(firstDate.getTime() + after *
											BeaconExpandoDataUtil.FIVE_MINUTES_IN_MILLIS));

								}
								chart.drawChart(chart.getConfiguration());
							}
						});
				}

			});


		// couple of utility buttons to make and clear fake data
		Button b = new Button("Make Fake Data", new ClickListener() {
			
			@Override
			public void buttonClick(ClickEvent event) {
				generateTestData();
			}

		});
		
		layout.addComponent(b);

		b = new Button("Clear All Data", new ClickListener() {
			
			@Override
			public void buttonClick(ClickEvent event) {
				try {
					BeaconExpandoDataUtil.clearFakeData(companyId);
					ls.removeAllItems();
					Notification.show("Cleared all data");
				}
				catch (Exception e) {
					Notification.show(
						"Error", e.getLocalizedMessage(), Notification.Type.WARNING_MESSAGE);
					e.printStackTrace();
				}
			}
		});
		
		layout.addComponent(b);

		layout.setSpacing(true);
		layout.setMargin(false);
		setContent(layout);


	}
	
	private void generateTestData() {
		// visit the server periodically to see when thread is done
		setPollInterval(1000);

		final ProgressBar progressBar = new ProgressBar();
		progressBar
				.setCaption("Creating some test data, this might take a while...");
		progressBar.setIndeterminate(true);
		layout.addComponent(progressBar);
		final UI ui = UI.getCurrent();
		new Thread() {
			@Override
			public void run() {
				try {
					BeaconExpandoDataUtil.makeFakeData(companyId);
					final List<String> newEvents = BeaconExpandoDataUtil
							.getAllEvents();

					ui.access(new Runnable() {

						@Override
						public void run() {
							ls.removeAllItems();
							ls.addItems(newEvents.toArray());
							Notification.show("Created fake data");
							layout.removeComponent(progressBar);
							setPollInterval(-1);
						}
					});
				} catch (final Exception e) {
					ui.access(new Runnable() {

						@Override
						public void run() {
							Notification.show("Error", e.getLocalizedMessage(),
									Notification.Type.WARNING_MESSAGE);
							layout.removeComponent(progressBar);
							setPollInterval(-1);
						}
					});
					e.printStackTrace();
				}
			}
		}.start();

	}
}
