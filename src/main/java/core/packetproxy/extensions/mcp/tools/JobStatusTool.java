package packetproxy.extensions.mcp.tools;

import static packetproxy.util.Logging.log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import packetproxy.model.Packet;
import packetproxy.model.Packets;

/**
 * ジョブの状況を取得するツール
 */
public class JobStatusTool extends AuthenticatedMCPTool {

	@Override
	public String getName() {
		return "get_job_status";
	}

	@Override
	public String getDescription() {
		return "Get status information for jobs created by send tools (resend_packet/bulk_send/call_vulcheck_helper). "
				+ "Returns job details including request/response packet counts and completion status.";
	}

	@Override
	public JsonObject getInputSchema() {
		JsonObject schema = new JsonObject();

		JsonObject jobIdProp = new JsonObject();
		jobIdProp.addProperty("type", "string");
		jobIdProp.addProperty("description", "Job ID to get status for. If not provided, returns status for all jobs.");
		schema.add("job_id", jobIdProp);

		return addAccessTokenToSchema(schema);
	}

	@Override
	protected JsonObject executeAuthenticated(JsonObject arguments) throws Exception {
		log("JobStatusTool called with arguments: " + getSafeArgumentsString(arguments));

		String jobId = arguments.has("job_id") ? arguments.get("job_id").getAsString() : null;

		if (jobId != null && !jobId.trim().isEmpty()) {
			// 特定のジョブの詳細を取得
			return getJobDetail(jobId);
		} else {
			// 全ジョブの概要を取得
			return getAllJobsStatus();
		}
	}

	/**
	 * 特定のジョブの詳細情報を取得
	 */
	private JsonObject getJobDetail(String jobId) throws Exception {
		log("JobStatusTool: Getting detail for job " + jobId);

		// job_idが一致するパケットを取得
		List<Packet> allPackets = Packets.getInstance().queryAll();
		List<Packet> jobPackets = new ArrayList<>();

		log("JobStatusTool: Searching for job " + jobId + " in " + allPackets.size() + " total packets");

		for (Packet packet : allPackets) {
			String packetJobId = packet.getJobId();
			if (packetJobId != null) {
				log("JobStatusTool: Packet " + packet.getId() + " has job_id: " + packetJobId);
			}
			if (jobId.equals(packetJobId)) {
				jobPackets.add(packet);
				log("JobStatusTool: Found matching packet " + packet.getId() + " for job " + jobId);
			}
		}

		log("JobStatusTool: Found " + jobPackets.size() + " packets for job " + jobId);

		if (jobPackets.isEmpty()) {
			throw new IllegalArgumentException("Job not found: " + jobId);
		}

		// temporary_id ごとにパケットを整理
		Map<String, JobRequest> jobRequests = new HashMap<>();

		for (Packet packet : jobPackets) {
			String temporaryId = packet.getTemporaryId();
			if (temporaryId == null || temporaryId.trim().isEmpty()) {
				continue;
			}

			JobRequest jobRequest = jobRequests.computeIfAbsent(temporaryId, k -> new JobRequest());
			jobRequest.temporaryId = temporaryId;

			if (packet.getDirection() == Packet.Direction.CLIENT) {
				// リクエストパケット
				jobRequest.requestPacketId = packet.getId();
				jobRequest.hasRequest = true;
			} else if (packet.getDirection() == Packet.Direction.SERVER) {
				// レスポンスパケット
				jobRequest.responsePacketId = packet.getId();
				jobRequest.hasResponse = true;
			}
		}

		// 結果を構築
		JsonObject result = new JsonObject();
		result.addProperty("job_id", jobId);
		result.addProperty("total_requests", jobRequests.size());

		int requestsSent = 0;
		int responsesReceived = 0;

		for (JobRequest jobRequest : jobRequests.values()) {
			if (jobRequest.hasRequest) {
				requestsSent++;
			}
			if (jobRequest.hasResponse) {
				responsesReceived++;
			}
		}

		result.addProperty("requests_sent", requestsSent);
		result.addProperty("responses_received", responsesReceived);

		// ジョブの状態を判定
		String status;
		if (requestsSent == 0) {
			status = "created";
		} else if (requestsSent < jobRequests.size()) {
			status = "sending_requests";
		} else if (responsesReceived == 0) {
			status = "requests_sent";
		} else if (responsesReceived < requestsSent) {
			status = "receiving_responses";
		} else {
			status = "completed";
		}
		result.addProperty("status", status);

		// 各リクエストの詳細
		JsonArray requestsArray = new JsonArray();
		for (JobRequest jobRequest : jobRequests.values()) {
			JsonObject reqObj = new JsonObject();
			reqObj.addProperty("temporary_id", jobRequest.temporaryId);
			reqObj.addProperty("has_request", jobRequest.hasRequest);
			reqObj.addProperty("has_response", jobRequest.hasResponse);

			if (jobRequest.hasRequest) {
				reqObj.addProperty("request_packet_id", jobRequest.requestPacketId);
			}
			if (jobRequest.hasResponse) {
				reqObj.addProperty("response_packet_id", jobRequest.responsePacketId);
			}

			requestsArray.add(reqObj);
		}
		result.add("requests", requestsArray);

		log("JobStatusTool: Job " + jobId + " has " + requestsSent + " requests sent, " + responsesReceived
				+ " responses received, status: " + status);

		return result;
	}

	/**
	 * 全ジョブの概要を取得
	 */
	private JsonObject getAllJobsStatus() throws Exception {
		log("JobStatusTool: Getting status for all jobs");

		// 全パケットからjob_idが設定されているものを取得
		List<Packet> allPackets = Packets.getInstance().queryAll();
		Map<String, JobSummary> jobs = new HashMap<>();

		log("JobStatusTool: Total packets in database: " + allPackets.size());

		int packetsWithJobId = 0;
		for (Packet packet : allPackets) {
			String jobId = packet.getJobId();
			if (jobId == null || jobId.trim().isEmpty()) {
				continue;
			}

			packetsWithJobId++;
			log("JobStatusTool: Found packet " + packet.getId() + " with job_id: " + jobId + ", temporary_id: "
					+ packet.getTemporaryId());

			JobSummary jobSummary = jobs.computeIfAbsent(jobId, k -> new JobSummary());
			jobSummary.jobId = jobId;

			String temporaryId = packet.getTemporaryId();
			if (temporaryId != null && !temporaryId.trim().isEmpty()) {
				jobSummary.temporaryIds.add(temporaryId);

				if (packet.getDirection() == Packet.Direction.CLIENT) {
					jobSummary.requestsSent++;
				} else if (packet.getDirection() == Packet.Direction.SERVER) {
					jobSummary.responsesReceived++;
				}
			}
		}

		log("JobStatusTool: Found " + packetsWithJobId + " packets with job_id");

		// 結果を構築
		JsonObject result = new JsonObject();
		result.addProperty("total_jobs", jobs.size());

		JsonArray jobsArray = new JsonArray();
		for (JobSummary jobSummary : jobs.values()) {
			JsonObject jobObj = new JsonObject();
			jobObj.addProperty("job_id", jobSummary.jobId);
			jobObj.addProperty("total_requests", jobSummary.temporaryIds.size());
			jobObj.addProperty("requests_sent", jobSummary.requestsSent);
			jobObj.addProperty("responses_received", jobSummary.responsesReceived);

			// ステータスを判定
			String status;
			if (jobSummary.requestsSent == 0) {
				status = "created";
			} else if (jobSummary.responsesReceived == 0) {
				status = "requests_sent";
			} else if (jobSummary.responsesReceived < jobSummary.requestsSent) {
				status = "receiving_responses";
			} else {
				status = "completed";
			}
			jobObj.addProperty("status", status);

			jobsArray.add(jobObj);
		}
		result.add("jobs", jobsArray);

		log("JobStatusTool: Found " + jobs.size() + " jobs");
		return result;
	}

	/**
	 * ジョブのリクエスト情報
	 */
	private static class JobRequest {
		String temporaryId;
		boolean hasRequest = false;
		boolean hasResponse = false;
		int requestPacketId = -1;
		int responsePacketId = -1;
	}

	/**
	 * ジョブの概要情報
	 */
	private static class JobSummary {
		String jobId;
		List<String> temporaryIds = new ArrayList<>();
		int requestsSent = 0;
		int responsesReceived = 0;
	}
}
