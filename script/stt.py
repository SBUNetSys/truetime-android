#!/usr/bin/python
import sys
import numpy as np
from datetime import datetime

def parse(fname):
    dictA={}
    for line in fname:
        line = line.strip()
        #print line
        if line == "":
            return
        fields = line.split()
        n = len(fields)
        ts = fields[1]  # timestamp
        data=fields[n-1]
        if ':' not in data:
            continue
        data2=data.split(':')
        if ',' not in data2[1]:
            continue
        data3=data2[1].split(',')
        key=data3[0]
        #print key, ts
        dictA[key]=ts
    return dictA

def main():
    if len(sys.argv) < 3:
        print "Error: Runing with param: 'sst.py fileA fileB'"
        return
    fileA = open(sys.argv[1], "r")
    fileB = open(sys.argv[2], "r")

    dictA=parse(fileA)
    dictB=parse(fileB)
    arr=[]

    for key in dictA:
        if key in dictB:
            d1 = datetime.strptime(dictA[key], "%H:%M:%S.%f")
            d2 = datetime.strptime(dictB[key], "%H:%M:%S.%f")
            #print dictA[key], dictB[key]
            mili=(d2-d1).total_seconds()
            arr.append(float(mili))
            
    print "avg: ", np.mean(arr)
    print "sd: ", np.std(arr)
    
main()
