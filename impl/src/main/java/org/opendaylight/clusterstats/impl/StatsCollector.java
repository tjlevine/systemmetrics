/*
 * Copyright © 2016 Cisco, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.clusterstats.impl;

import com.google.common.collect.ImmutableList;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.get.cluster.members.output.ClusterMembers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.get.cluster.members.output.ClusterMembersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.get.shard.mbean.output.FollowerInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.get.shard.mbean.output.FollowerInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.get.stats.output.DataPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.get.stats.output.DataPointsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.GetClusterMembersOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.GetClusterMembersOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.GetGeneralRuntimeInfoMbeanInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.GetGeneralRuntimeInfoMbeanOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.GetGeneralRuntimeInfoMbeanOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.GetShardMbeanInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.GetShardMbeanOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.GetShardMbeanOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.GetStatsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.GetStatsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.stats.reflector.rev150105.GetStatsOutputBuilder;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

/**
 * @author Tyler Levine, tylevine@cisco.com
 * @version 1.0
 * @since 2016-08-10
 */
public class StatsCollector {

    private static final Logger LOG = LoggerFactory.getLogger(StatsCollector.class);

    private static String generateTSDRKey(final GetStatsInput request) {
        return TsdrKeyFactory.createEncodedTsdrKey(
                request.getNodeId(),
                request.getDataCategory(),
                request.getMetricName());
    }

    private static void setRequestHeaders(final HttpURLConnection conn) {
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization",  "Basic YWRtaW46YWRtaW4=");
    }

    private static GetStatsOutput jsonToStatsOutput(final InputStream jsonData) {
        final JsonReader reader = Json.createReader(jsonData);
        final JsonObject outObj = reader.readObject();
        final JsonArray results = outObj.getJsonArray("results");
        final JsonObject result = (JsonObject)results.get(0);
        final JsonArray series = result.getJsonArray("series");
        final JsonObject seriesobj = (JsonObject)series.get(0);
        final JsonArray metricRecords = seriesobj.getJsonArray("values");
        reader.close();
        return buildStatsOutput(metricRecords);
    }

    private static DataPoints buildDataPoint(final String metricName, final String metricValue, final String dataCategory, final String timeStamp, final String nodeId) {
        return new DataPointsBuilder()
                .setMetricName(metricName)
                .setMetricValue(metricValue)
                .setDataCategory(dataCategory)
                .setTimeStamp(timeStamp)
                .setNodeId(nodeId)
                .build();
    }

    private static GetStatsOutput buildStatsOutput(final JsonArray metricRecords) {

        LOG.info("Got {} tsdr metric records", metricRecords.size());

        final List<DataPoints> dataPoints = new ArrayList<>();

        for (int i = 0; i < metricRecords.size(); i++) {
            final JsonArray firstMetric = (JsonArray) metricRecords.get(i);
            final String metricName = (String) firstMetric.getString(1);
            final String metricValue = String.valueOf(firstMetric.get(2));
            final String timeStamp = (String) firstMetric.getString(0);
            final String nodeId = (String) firstMetric.getString(3);
            /* TODO: Can we remove tsdrDC ??*/
            final String tsdrDC = "tsdr";

            dataPoints.add(buildDataPoint(metricName, metricValue, tsdrDC, timeStamp, nodeId));
        }

        return new GetStatsOutputBuilder()
                .setDataPoints(dataPoints)
                .build();
    }

    private static GetStatsOutput buildEmptyStatsOutput() {
        return new GetStatsOutputBuilder().build();
    }

    public static GetStatsOutput collectForRequest(final GetStatsInput request) throws IOException {
        final String requestURL = RestUrlFactory.getInfluxUrl();

        LOG.info("Making stats request to URL: {}", requestURL);

        final HttpURLConnection conn = (HttpURLConnection) new URL(requestURL).openConnection();

        setRequestHeaders(conn);

        return jsonToStatsOutput(conn.getInputStream());
    }

    private static GetClusterMembersOutput buildClusterMembersOutput(final Map<IpAddress, LocalShardsAndMemberName> clusterMemberInfo) throws IOException {

        final List<ClusterMembers> clusterMembers = new ArrayList<>();

        for (Map.Entry<IpAddress, LocalShardsAndMemberName> clusterMember : clusterMemberInfo.entrySet()) {

            final ClusterMembersBuilder cmBuilder = new ClusterMembersBuilder()
            .setLocalShards(clusterMember.getValue().getLocalShards())
            .setMemberAddress(clusterMember.getKey())
            .setMemberName(clusterMember.getValue().getMemberName())
            .setVoter(true); // TODO: return accurate voter info

            clusterMembers.add(cmBuilder.build());
        }

        return new GetClusterMembersOutputBuilder()
                .setClusterMembers(clusterMembers)
                .build();
    }

    private static final Pattern pattern = Pattern.compile("(.*?)@(.+?):");

    private static IpAddress parseIpFromAkkaaddress(final String bucketVersion) {
        LOG.debug("parsing ip from bucket version string: {}", bucketVersion);
        final Matcher matcher = pattern.matcher(bucketVersion);

        matcher.find();

        final String match = matcher.group(2);
        return new IpAddress(match.toCharArray());
    }

    private static String parseLocalShardValue(final JsonValue value) {
        return value.toString().replaceAll("\"", "");
    }

    private static LocalShardsAndMemberName parseLocalShardsAndMemberNameFromJson(final InputStream jsonData) {
        final JsonReader reader = Json.createReader(jsonData);
        final JsonObject valueObject = reader.readObject().getJsonObject("value");
        final JsonArray localShards = valueObject.getJsonArray("LocalShards");
        final String memberName = valueObject.getString("MemberName");

        final List<String> localShardsList = localShards.stream().map(StatsCollector::parseLocalShardValue).collect(Collectors.toList());

        return new LocalShardsAndMemberName(memberName, localShardsList);
    }

    private static LocalShardsAndMemberName getLocalShardsAndMemberNameForClusterMember(final IpAddress clusterMemberIpAddress) throws IOException {
        final String requestURL = RestUrlFactory.getShardManagerUrl(clusterMemberIpAddress);

        LOG.debug("Making shard manager request to {}", requestURL);

        final HttpURLConnection conn = (HttpURLConnection) new URL(requestURL).openConnection();

        setRequestHeaders(conn);

        return parseLocalShardsAndMemberNameFromJson(conn.getInputStream());
    }

    private static GetClusterMembersOutput jsonToClusterMembersOutput(final InputStream jsonData) throws IOException {
        final JsonReader jsonReader = Json.createReader(jsonData);
        final JsonObject responseObject = jsonReader.readObject();

        JsonReader clusterstatusReader = Json.createReader(new StringReader(responseObject.getJsonObject("value").getString("ClusterStatus")));
        final JsonObject clusterstatusObject = clusterstatusReader.readObject();
        clusterstatusReader.close();

        final JsonArray membersObject = clusterstatusObject.getJsonArray("members");

        final Map<IpAddress, LocalShardsAndMemberName> ipToLocalShardsAndMemberName = new HashMap<>();

        for(int i=0; i<membersObject.size(); i++){
            JsonObject memberObject = membersObject.getJsonObject(i);
            final IpAddress clusterMemberIp = parseIpFromAkkaaddress(memberObject.getString("address"));
            ipToLocalShardsAndMemberName.put(clusterMemberIp, getLocalShardsAndMemberNameForClusterMember(clusterMemberIp));
        }
        jsonReader.close();

        return buildClusterMembersOutput(ipToLocalShardsAndMemberName);
    }

    public static GetClusterMembersOutput getClusterMembers() throws IOException {

        final String requestUrl = RestUrlFactory.getClusterMemberUrl();

        LOG.debug("Making cluster member request to {}", requestUrl);

        final HttpURLConnection conn = (HttpURLConnection) new URL(requestUrl).openConnection();

        setRequestHeaders(conn);

        return jsonToClusterMembersOutput(conn.getInputStream());
    }


    private static void checkStatus(String status) throws IOException {
        if (!(status.equals("operational") || status.equals("config"))) {
            throw new IOException("Status must be operational or config");
        }
    }

    private static JsonObject getJsonObject(InputStream jsonData) {
        JsonReader jsonReader = Json.createReader(jsonData);
        JsonObject object = jsonReader.readObject();
        jsonReader.close();

        return object;
    }

    public static GetGeneralRuntimeInfoMbeanOutput getGeneralRuntimeInfoStats(GetGeneralRuntimeInfoMbeanInput input) throws IOException {
        String query =  generateGeneralRuntimeInfoQuery(input);

        LOG.debug("Making GeneralRuntimeInfo MBean stats request to URL: {}", query);

        HttpURLConnection conn = (HttpURLConnection) new URL(query).openConnection();
        setRequestHeaders(conn);

        return jsonToGeneralRuntimeInfoOutput(conn.getInputStream());
    }

    private static String generateGeneralRuntimeInfoQuery(GetGeneralRuntimeInfoMbeanInput input) throws IOException {
        IpAddress address = input.getMemberAddress();
        String status = input.getStatus();

        checkStatus(status);

        return RestUrlFactory.getGeneralRuntimeInfoUrl(address, status);
    }

    private static GetGeneralRuntimeInfoMbeanOutput jsonToGeneralRuntimeInfoOutput(InputStream jsonData) {
        JsonObject object = getJsonObject(jsonData);
        JsonObject value = object.getJsonObject("value");

        return new GetGeneralRuntimeInfoMbeanOutputBuilder()
                .setTimestamp(object.getJsonNumber("timestamp").toString())
                .setTransactionCreationRateLimit(value.getJsonNumber("TransactionCreationRateLimit").toString())
                .build();
    }

    public static GetShardMbeanOutput getShardStats(GetShardMbeanInput input) throws IOException {
        String query = generateShardQuery(input);

        LOG.debug("Making Shard MBean stats request to URL: {}", query);

        HttpURLConnection conn = (HttpURLConnection) new URL(query).openConnection();
        setRequestHeaders(conn);

        return jsonToShardOutput(conn.getInputStream());
    }

    private static String generateShardQuery(GetShardMbeanInput input) throws IOException {
        IpAddress address = input.getMemberAddress();
        String name = input.getMemberName();
        String status = input.getStatus();
        String shard = input.getShard();

        checkStatus(status);

        return RestUrlFactory.getShardUrl(address, name, status, shard);
    }

    private static GetShardMbeanOutput jsonToShardOutput(InputStream jsonData) {
        JsonObject object = getJsonObject(jsonData);
        JsonObject value = object.getJsonObject("value");

        GetShardMbeanOutputBuilder outputBuilder = new GetShardMbeanOutputBuilder();

        outputBuilder.setTimestamp(object.getJsonNumber("timestamp").toString())
                .setInMemoryJournalLogSize(value.getInt("InMemoryJournalLogSize"))
                .setReplicatedToAllIndex(value.getInt("ReplicatedToAllIndex"))
                .setLastIndex(value.getInt("LastIndex"))
                .setLastApplied(value.getInt("LastApplied"))
                .setPendingTxCommitQueueSize(value.getInt("PendingTxCommitQueueSize"))
                .setCommitIndex(value.getInt("CommitIndex"))
                .setRaftState(value.getString("RaftState"));

        if (value.getString("RaftState").equals("Follower")) {
            return outputBuilder.build();
        }

        else {
            JsonArray followerInfoArray = value.getJsonArray("FollowerInfo");
            List<FollowerInfo> followerInfoList = generateFollowerInfoList(followerInfoArray);
            return outputBuilder
                    .setFollowerInfo(followerInfoList)
                    .build();
        }
    }

    private static List<FollowerInfo> generateFollowerInfoList(JsonArray array) {
        List<FollowerInfo> followerInfoList = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject followerObject = array.getJsonObject(i);

            followerInfoList.add(new FollowerInfoBuilder()
                    .setId(followerObject.getString("id"))
                    .setTimeSinceLastActivity(followerObject.getString("timeSinceLastActivity"))
                    .setMatchIndex(followerObject.getInt("matchIndex"))
                    .build());
        }

        return followerInfoList;
    }
}

final class LocalShardsAndMemberName {
    private final List<String> localShards;
    private final String memberName;

    LocalShardsAndMemberName(final String memberName, final List<String> localShards) {
        this.memberName = memberName;
        this.localShards = ImmutableList.copyOf(localShards);
    }

    public List<String> getLocalShards() {
        return localShards;
    }

    public String getMemberName() {
        return memberName;
    }
}
