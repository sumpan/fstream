angular.module('FStreamApp.services', []).
factory('ratesService', function($rootScope, $timeout) {
	
	//
	// Private
	//
	
	var stompClient,
		publishEvent = function (eventName, frame){
			$timeout(function() {
				var event = frame && angular.fromJson(frame.body);
	        	$rootScope.$broadcast(eventName, event); 
			});
		};
 
	return {
		
		//
		// Methods
		//
		
		connect: function() {
			stompClient = Stomp.over(new SockJS('/server'));
			
			stompClient.connect({}, function(frame) {
				publishEvent("connected");
				
		        stompClient.subscribe('/topic/rates', function(frame){
		        	publishEvent("rate", frame);
		        });
		        
		        stompClient.subscribe('/topic/alerts', function(frame){
		        	publishEvent("alert", frame);
		        });
		        
		        stompClient.subscribe('/topic/metrics', function(frame){
		        	publishEvent("metric", frame);
		        });
		        
		        stompClient.subscribe('/topic/commands', function(frame){
		        	publishEvent("command", frame);
		        });                
		    });
			
		},
		
	    disconnect: function() {
	        stompClient.disconnect();
	        
	        publishEvent("disconnected");
	    },
	    
	    register: function(instrument){
		    stompClient.send("/web/register", {}, angular.toJson({ 'instrument': instrument }));
	    }
	    
	} 
}).
factory('chartService', function($rootScope) {
	Highcharts.setOptions({
		global : {
			useUTC : false
		}
	});
	
	var chart,
	    size = 50,
	    enabled = true,
	    series;

	return {
		init: function() {
			// Create the chart
			chart = new Highcharts.StockChart({
				
				credits: {
					enabled: false
				},
				
				chart : {
					renderTo: 'chart-container',
					events : {
						load : function() {
							series = this.series;
						}
					},
					
					height: 600,
					zoomType: 'xy'
				},
				
				rangeSelector: {
					buttons: [{
						count: 1,
						type: 'minute',
						text: '1M'
					}, {
						count: 5,
						type: 'minute',
						text: '5M'
					}, {
						type: 'all',
						text: 'All'
					}],
					inputEnabled: false,
					selected: 0
				},
				
	            xAxis: {
	                type: 'datetime',
	            },
	            
		        tooltip: {
		            crosshairs: [true, true]
		        },			    
			    
				series : [{
					id: 'Ask',
					name : 'Ask',
					step: true,
					data : []
				}, {
					name : 'Bid',
					step: true,
					data : []
				}, {
					type: 'flags',
					color: '#C12E2A',
					onSeries: 'Ask',
					fillColor: '#D9534F',
			        width: 25,
			        style: {
			        	color: 'white'
			        },
			        states: {
			        	hover: {
			        		fillColor: '#C12E2A'
			        	}
			        }			        
				}]
			});
		},
		
    	addRate: function(rate) {
    		var shift = series[0].data.length >= size,
    		    animate = false;
    		series[0].addPoint([rate.dateTime, rate.ask], false, shift, animate);
    		series[1].addPoint([rate.dateTime, rate.bid], enabled, shift, animate);
    	},
    	
		addAlert: function(alert) {
			series[2].addPoint({
				x: alert.dateTime,
				title: 'Alert',
				text: angular.toJson(alert, true)
			}, enabled, false);
		},
		
		enable: function () {
			if (!enabled) {
				enabled = true;
				chart.redraw();
			} 
		},
		
		disable: function () {
			enabled = false;
		}
    };	
});