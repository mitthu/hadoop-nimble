#!/bin/bash
# Created on: 27-Sep-2022, 07:33 pm EDT
# Added by:   mitthu (Aditya Basu)
# ----
# Use:
# - Snapshot NN and get XML logs

# Execute inside namenode
function nn() {
	# Requires JAVA_HOME and PATH being set in /root/.profile
	lxc exec namenode -- bash -c "source /root/.profile; $*"
}

# Snapshot
function snapshot() {
	echo "Snapshot Namenode:"
	nn hdfs dfsadmin -safemode enter
	nn hdfs dfsadmin -saveNamespace
	nn hdfs dfsadmin -safemode leave
}

# Convert binary fsimage to XML
fsimage=$(cat << 'EOF'
cd /tmp/hadoop-root/dfs/name/current;
for f in `find . -type f -iregex '^./fsimage\_[0-9]+$'`; do
	echo "Processing: $f";
	hdfs oiv -p XML -i $f -o $f.xml;
	cp $f.xml /tmp/xmlfiles;
done;
EOF
)

# Convert binary editlogs to XML
editlogs=$(cat << 'EOF'
cd /tmp/hadoop-root/dfs/name/current;
for f in `find . -type f -iregex '^./edits\_[0-9\-]+$'`; do
	echo "Processing: $f";
	hdfs oev -i $f -o $f.xml;
	cp $f.xml /tmp/xmlfiles;
done;
EOF
)


# Process
#########
nn "rm -rf /tmp/xmlfiles; mkdir /tmp/xmlfiles"
rm -rf xmlfiles

snapshot
nn $fsimage
nn $editlogs

# Copy here everything & delete non-xml files
lxc file pull -r namenode/tmp/xmlfiles/ .
