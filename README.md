# Liferay+Vaadin Beacon Data Visualization Demo App

This demo app consists of three portlets based on [Vaadin](http://vaadin.com)
and [Liferay
Portal](http://www.liferay.com/community/liferay-projects/liferay-portal).

It is built using the ["Self-Contained" Vaadin application](https://www.liferay.com/web/matti/blog/-/blogs/using-self-contained-approach-to-package-vaadin-portlets) approach, which means that Vaadin is packaged along with the app and deployed to Liferay.

## Building and deploying the Demo

First, you must have Liferay 6.1 or 6.2 deployed and a Maven profile that points to it.
You must also have Maven installed. To build and use:

1. Command line:

Fork this repository to your local machine
mvn -P (profile-name) install liferay:deploy

2. Eclipse:
Import this as a new Maven project, and right-click on the project and select *Liferay->Maven->liferay:deploy*.

The first time it is built, it may take a few minutes as the widgetset is compiled. Once deployed, you should see
a message at the end of the Liferay console `... [PortletHotDeployListener:495] 3 portlets for mavenizedbeacons-0.0.1-SNAPSHOT are available for use`. 


## Usage

Once you build and deploy this project, you will have three new portlets -- add them all to a page, and click *Make Fake Data* to generate some demo data within Liferay's Expando data table. Then, select an event to view the data. Clicking on the graph will load individual ping data into the Ping Table, and clicking on rows in the Ping Table will update the graph to zoom in on the selected row.

[Imgur](http://i.imgur.com/uYZaToK.png)

You can also use the Beacon Proximity Browser to visualize individual beacon data using a slightly different type of chart.

## License

This software, *Liferay+Vaadin Beacon Data Visualization Demo App*, is free software ("Licensed
Software"); you can redistribute it and/or modify it under the terms of the [GNU
Lesser General Public License](http://www.gnu.org/licenses/lgpl-2.1.html) as
published by the Free Software Foundation; either version 2.1 of the License, or
(at your option) any later version.

This library is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; including but not limited to, the implied warranty of MERCHANTABILITY,
NONINFRINGEMENT, or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
Public License for more details.

You should have received a copy of the [GNU Lesser General Public
License](http://www.gnu.org/licenses/lgpl-2.1.html) along with this library; if
not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth
Floor, Boston, MA 02110-1301 USA