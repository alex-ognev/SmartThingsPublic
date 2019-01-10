/**
 *  Particle Sensor
 *
 *  Copyright 2019 Alex Ognev
 *
 */
 
preferences {
    input("deviceId", "text", title: "Device ID")
    input("token", "text", title: "Access Token")
}

metadata {

    definition (name: "Particle Sensor", namespace: "com.onev", author: "Alex Ognev") {
		capability "Illuminance Measurement"
		capability "Relative Humidity Measurement"
		capability "Temperature Measurement"
        capability "Refresh"
        capability "Polling"
	}

    tiles(scale: 2) {
		valueTile("temperature", "device.temperature", decoration: "flat", width: 2, height: 2) {
            state "temperature", label:'${currentValue} °F',
            backgroundColors:[
                [value: 31, color: "#153591"],
                [value: 44, color: "#1e9cbb"],
                [value: 59, color: "#90d2a7"],
                [value: 74, color: "#44b621"],
                [value: 84, color: "#f1d801"],
                [value: 95, color: "#d04e00"],
                [value: 96, color: "#bc2323"]
            ]
        }
		
		valueTile("humidity", "device.humidity", decoration: "flat", width: 2, height: 2) {
            state "humidity", label:'${currentValue} %'
        }
        
        valueTile("illuminance", "device.illuminance", decoration: "flat", width: 2, height: 2) {
            state "illuminance", label:'${currentValue} lux'
        }

		main(["temperature"])
        details(["temperature", "humidity", "illuminance"])
	}        
}

def parse(String description) {
	log.error "parse() is not supported for cloud-linked devices";
    return null
}

def poll() {
	log.debug "Executing 'poll'"
	 checkStatus ()	
}

def refresh() {
	log.debug "executing 'refresh'"
	checkStatus()
}

def start() {
	runEvery1Minute(refresh)
}

def checkStatus(){
	def url =  "https://api.particle.io/v1/devices/${deviceId}/measurements?access_token=${token}"
	log.debug "Check status @ ${url}"
    try {
        httpGet(url) {resp ->           
            log.debug "resp data: ${resp.data}"
            def measurements = parseJson(resp.data.result)

            sendEvent(name: "temperature", value: "${measurements.temp}")
            sendEvent(name: "humidity", value: "${measurements.humidity}")
            sendEvent(name: "illuminance", value: "${measurements.lux}")
            log.debug  "t = ${measurements.temp}, h = ${measurements.humidity}% light = ${measurements.lux} lux"
        }
    } catch (e) {
        log.error "something went wrong: $e"
    }
}
