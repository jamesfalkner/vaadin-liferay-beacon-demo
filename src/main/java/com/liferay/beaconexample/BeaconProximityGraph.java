
package com.liferay.beaconexample;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

import javax.portlet.PortletSession;

import com.liferay.beaconexample.util.BeaconExpandoDataUtil;
import com.liferay.portal.kernel.util.Validator;
import com.vaadin.addon.charts.Chart;
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
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.NativeSelect;
import com.vaadin.ui.Notification;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

/**
 * This Vaadin portlet visualizes several time-series data series on a single
 * chart. The data is expected to be in a session variable shared with other
 * Vaadin portlets like the BeaconRegionGraph.
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
public class BeaconProximityGraph extends UI implements Serializable {

	private VerticalLayout layout = new VerticalLayout();
	private HorizontalLayout chartLayout = new HorizontalLayout();
	private LiferayIPC ipc;
	private String selectedEvent;
	private Date firstDate;
	private Map<String, Map<String, Container>> selectedEventData;

	@Override
	protected void init(VaadinRequest request) {

		// the IPC listener
		ipc = new LiferayIPC();
		ipc.extend(this);

		// make the UI
		layout.setMargin(false);
		setContent(layout);

		final NativeSelect ls = new NativeSelect("Beacon");
		ls.setEnabled(false);
		layout.addComponent(ls);
		layout.addComponent(chartLayout);

		// add listener to fetch session data and populate the NativeSelect
		// with a list of beacon data points
		ipc.addLiferayIPCEventListener(
			BeaconExpandoDataUtil.IPC_SELECTED_EVENT, new LiferayIPCEventListener() {

				@SuppressWarnings("unchecked")
				@Override
				public void eventReceived(LiferayIPCEvent event) {

					String evt = event.getData();
					
					if (Validator.isNull(evt))
						return;
					
					PortletSession psession = VaadinPortletService.getCurrentPortletRequest()
							.getPortletSession();
					
					selectedEventData =
						(Map<String, Map<String, Container>>) psession.getAttribute(
							BeaconExpandoDataUtil.IPC_BEACON_CHART_DATA,
							PortletSession.APPLICATION_SCOPE);

					firstDate =
						(Date) psession.getAttribute(
							BeaconExpandoDataUtil.IPC_REGION_FIRST_DATE,
							PortletSession.APPLICATION_SCOPE);

					selectedEvent = evt;

					chartLayout.removeAllComponents();

					ls.setEnabled(false);
					
					ls.removeAllItems();

					if (Validator.isNotNull(selectedEventData) &&
						Validator.isNotNull(firstDate)) {
						for (String beaconName : selectedEventData.keySet()) {
							ls.addItem(beaconName);
						}
					}
					ls.setEnabled(true);

				}
			});

		// add listener to show the graph data based on what the user chooses
		// from the select element.
		ls.addValueChangeListener(new ValueChangeListener() {

			@Override
			public void valueChange(ValueChangeEvent evt) {

				Object newValue = ls.getValue();
				if (Validator.isNull(newValue)) {
					return;
				}

				final String beaconName = newValue.toString();

				Map<String, Container> proxData = selectedEventData.get(beaconName);

				try {
					Chart chart =
						BeaconExpandoDataUtil.getBeaconProximityChartForEvent(
							selectedEvent, beaconName, proxData, firstDate);

					chartLayout.removeAllComponents();
					chartLayout.addComponent(chart);
					chartLayout.setExpandRatio(chart, 1);
				}
				catch (Exception e) {
				  Notification.show(
						"Error", e.getLocalizedMessage(), Notification.Type.WARNING_MESSAGE);
					e.printStackTrace();
				}

			}
		});
	}
}
