/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Virtual Thermostat
 *
 *  Author: SmartThings
 */
 

definition(
    name: "Environment Logger",
    namespace: "com.ognev",
    author: "Alex Ognev",
    description: "Control a space heater or window air conditioner in conjunction with any temperature sensor, like a SmartSense Multi.",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch@2x.png",
    pausable: true
)

preferences {
	section("Choose a sensor... "){
		input "sensor", "capability.temperatureMeasurement", title: "Sensor"
        input "switches", "capability.switch", title: "Switches to log", multiple: true
        input "coreSensors", "device.particleSensor", title: "Core Sensors", multiple: true
	}
}

def subscribeToEvents() {
	state.outside = [:]


	getOutsideTemperature();
    pollCores();
    runEvery1Minute(pollCores);
    runEvery15Minutes(getOutsideTemperature); 

	log.debug "subscribing to events..."
  	subscribe(sensor, "temperature", measurementHandler)
   
   	subscribe(switches, "switch", switchChanged)
    
    subscribe(location, "mode", measurementHandler)
	subscribe(location, "sunset", measurementHandler)
	subscribe(location, "sunrise", measurementHandler)
}

def initialize() {
}

def installed() {	
	subscribeToEvents()
}

def updated() {
	unsubscribe()
	subscribeToEvents()
}

def pollCores() {
	coreSensors.poll();
}

def parseEventValue(evt, unitsLenght) {
	log.debug(evt.data);
    def data = parseJson(evt.data);    
    def label = data.microDeviceTile.label;
    return Double.parseDouble(label.take(label.length() - unitsLenght));
}

def measurementHandler(evt) {
	logValues()
}

def switchState() {
	def state = [:]
    def onOff = switches.currentSwitch
    def level = switches.currentLevel
    def name = switches.displayName
    def i = 0;
    for (i = 0; i < name.size(); ++i) {
    	state[name[i]] = onOff[i] == "on" ? level[i] : 0
    }
    return state
}

def allMeasurements () {
	def temp = coreSensors.currentTemperature
    def humidity = coreSensors.currentHumidity
    def light = coreSensors.currentIlluminance
    def names = coreSensors.displayName
    def values = [:]
    for (def i = 0; i < names.size(); ++i) {
    	def values_i = [:]
        values_i.name = names[i]
        values_i.temperature = temp[i]
        values_i.humidity = humidity[i]
        values_i.light = light[i]
        values[names[i]] = values_i
    }
    log.debug "${values}"
    return values
}


def convertTemperature(k) {
 	return (k - 273.15) * 1.8 + 32;
}

def getOutsideTemperature() {
	def lat = "41.895229"
    def lon = "-87.6266161"
    def apiKey = "43e13a4d8f05116f83bbee763cc8eb1e";
	def url = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lon + 
        	"&appid=" + apiKey
            
    try {
        httpGet(url) { resp -> 
                state.outside.t = convertTemperature(resp.data.main.temp);
                state.outside.h = resp.data.main.humidity;
                state.outside.pressure = resp.data.main.pressure;
                state.outside.tmin = convertTemperature(resp.data.main.temp_min);
                state.outside.tmax = convertTemperature(resp.data.main.temp_max);
                logValues();
        }
    } catch (e) {
        log.error "something went wrong: $e"
    }
}

def logValues() {
    def t = sensor.currentState("temperature").value; 
    def h = sensor.currentState("humidity").value;
    def lux = sensor.currentState("illuminance").value;
    def uv  = sensor.currentState("ultravioletIndex").value;       
    
    def now = new Date();
    def tz = location.timeZone
    def date = now.format("yyyyMMdd", tz);
    def time = now.time;
    
    def sunParams = getSunriseAndSunset()
    def sunIsUp = now >= sunParams.sunrise && now <= sunParams.sunset
    def mode = location.currentMode.getName()
    
    def allMeasurements = allMeasurements();
    def mainMeasurement = [:]
    mainMeasurement.name = "multi"
    mainMeasurement.temperature = t
    mainMeasurement.humidity = h
    mainMeasurement.light = lux
    allMeasurements[mainMeasurement.name] = mainMeasurement
       
    def params = [
        uri: "https://focus-ce3dd.firebaseio.com/environment/${date}/${time}.json",
        body: [
        	mode: mode,
            daylight: sunIsUp,
        	time: time,
            temperature: t,
            humidity: h,
            illuminance: lux,
            uvindex: uv, 
            lights: switchState(),
            outside: state.outside,
            locations: allMeasurements
        ]
    ]

    try {
        httpPutJson(params)
    } catch (e) {
        log.error "something went wrong: $e"
    }
}

def switchChanged(evt) {
 	def now = new Date();
    def tz = location.timeZone
    def date = now.format("yyyyMMdd", tz);
    def time = now.time;
   
    def params = [
        uri: "https://focus-ce3dd.firebaseio.com/events/${date}/${time}.json",
        body: [
            lights: switchState()
        ]
    ]

    try {
        httpPutJson(params) { resp ->
            log.debug "response data: ${resp.data}"
        }
    } catch (e) {
        log.error "something went wrong: $e"
    }
}