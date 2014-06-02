
package com.liferay.beaconexample;

import java.util.Map;

import javax.portlet.PortletSession;

import com.liferay.beaconexample.util.BeaconExpandoDataUtil;
import com.liferay.portal.kernel.util.Validator;
import com.vaadin.addon.ipcforliferay.LiferayIPC;
import com.vaadin.addon.ipcforliferay.event.LiferayIPCEvent;
import com.vaadin.addon.ipcforliferay.event.LiferayIPCEventListener;
import com.vaadin.annotations.PreserveOnRefresh;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Widgetset;
import com.vaadin.data.Container;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinService;
import com.vaadin.server.WrappedPortletSession;
import com.vaadin.ui.AbstractSelect.ItemCaptionMode;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Table;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

/**
 * This Vaadin portlet shows a table of data based on the data shared with it
 * in the application's session. Clicking on a row sends an IPC event to other
 * portlets to show detail about the clicked row.
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
public class BeaconPingTable extends UI {

	private LiferayIPC ipc;
	private VerticalLayout layout = new VerticalLayout();

	@Override
	public void init(VaadinRequest request) {

		// The IPC sender/receiver
		
		ipc = new LiferayIPC();
		ipc.extend(this);

		// make the UI
		
		layout.setMargin(false);

		setContent(layout);

		final Table t = new Table();
		t.setItemCaptionMode(ItemCaptionMode.ID);
		t.setSizeFull();
		t.setSelectable(true);

		// send an IPC event on row clickage
		t.addValueChangeListener(new ValueChangeListener() {

			@Override
			public void valueChange(ValueChangeEvent event) {

				Object val = t.getValue();

				ipc.sendEvent(
					BeaconExpandoDataUtil.IPC_SELECTED_BUCKET, Validator.isNotNull(val)
						? val.toString() : "");
			}
		});

		layout.addComponent(t);

		// listen for event selects from the BeaconRegionGraph portlet, and update
		// the table (re-bind the Table to a new set of data)
		ipc.addLiferayIPCEventListener(
			BeaconExpandoDataUtil.IPC_SELECTED_REGION, new LiferayIPCEventListener() {

				@SuppressWarnings({
					"unchecked"
				})
				public void eventReceived(LiferayIPCEvent event) {

					try {
						
						// get the data from session
						PortletSession ps =
										((WrappedPortletSession) (VaadinService.getCurrentRequest().getWrappedSession())).getPortletSession();

						Map<String, Container> data =
							(Map<String, Container>) ps.getAttribute(
								BeaconExpandoDataUtil.IPC_REGION_CHART_DATA,
								PortletSession.APPLICATION_SCOPE);

						Container regionData = data.get(event.getData());

						if (Validator.isNotNull(regionData)) {
							// re-bind data table to new data
							t.setContainerDataSource(regionData);
							t.setCaption("Pings for Region: " + event.getData());
						}
					}
					catch (Exception e) {
						Notification.show(
							"Error", e.getLocalizedMessage(),
							Notification.Type.WARNING_MESSAGE);
						e.printStackTrace();
					}
				}
			});
	}

}
