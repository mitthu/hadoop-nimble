Follow the instructions in dev-setup to format HDFS.


In local machine do the following:

```
export JAVA_HOME=/usr/lib/jvm/default-java
export PATH=/opt/hadoop-3.3.3/bin:$PATH
export HADOOP_CLASSPATH=${JAVA_HOME}/lib/tools.jar
```


Then compile the mapreduce program. For example, the WordCount.java program in: https://hadoop.apache.org/docs/stable/hadoop-mapreduce-client/hadoop-mapreduce-client-core/MapReduceTutorial.html.


```
hadoop com.sun.tools.javac.Main WordCount.java
jar cf wc.jar WordCount*.class
```

Then copy this program to namenode:


```
lxc file push wc.jar namenode/root/wc.jar
```

Now we need to create the input folder in HDFS and copy the files.
First, lets copy the files to the namenodes' local filesystem (this is not HDFS):

```
lxc file push input1.txt namenode/root/input1.txt
lxc file push input2.txt namenode/root/input2.txt
```

Then run the following:

```
lxc exec namenode -- /opt/hadoop-3.3.3/bin/hdfs dfs -mkdir /user
lxc exec namenode -- /opt/hadoop-3.3.3/bin/hdfs dfs -mkdir /user/sangel
lxc exec namenode -- /opt/hadoop-3.3.3/bin/hdfs dfs -mkdir /user/sangel/inputs
lxc exec namenode -- /opt/hadoop-3.3.3/bin/hdfs dfs -copyFromLocal input1.txt /user/sangel/inputs/input1.txt
lxc exec namenode -- /opt/hadoop-3.3.3/bin/hdfs dfs -copyFromLocal input2.txt /user/sangel/inputs/input2.txt
```

Verify files are stored in HDFS

```
lxc exec namenode -- /opt/hadoop-3.3.3/bin/hdfs dfs -ls /user/sangel/inputs/
lxc exec namenode -- /opt/hadoop-3.3.3/bin/hdfs dfs -cat /user/sangel/inputs/input1.txt
```

Run MapReduce:

```
lxc exec namenode -- /opt/hadoop-3.3.3/bin/hadoop jar wc.jar WordCount /user/sangel/inputs /user/sangel/outputs
```

Inspect outputs:

```
lxc exec namenode -- /opt/hadoop-3.3.3/bin/hdfs dfs -ls /user/sangel/outputs/
lxc exec namenode -- /opt/hadoop-3.3.3/bin/hdfs dfs -cat /user/sangel/outputs/part-r-00000
```
