# -*- coding: utf-8 -*-
"""
Created on Mon Oct 10 15:58:28 2022

@author: SEVENDI ELDRIGE RIFKI POLUAN
"""

# pip install paho-mqtt
import paho.mqtt.client as mqtt
from random import randint
import datetime
import time 
import requests
 
broker_addres = 'test.mosquitto.org'
port = 1883
client_id = 'ea9b8de9-56b2-47f4-b0f2-e469bdc34497' 
     
def on_connect(client, userdata, flags, rc):
    
    if rc == 0:
        print("Connected to MQTT Broker!")
        topic = "/sevendi/smartinsoles" 
        client.subscribe(topic)
    else:
        print("Failed to connect, return code %d\n", rc)
  
def on_message(client, userdata, msg):
    
    content = msg.payload.decode('utf-8')
    top = msg.topic

    print('Received', content, top) 
    
client = mqtt.Client(client_id=client_id, clean_session=False, transport="tcp")
client.on_connect = on_connect
client.on_message = on_message 
client.connect(broker_addres, port)        
client.loop_forever()
 