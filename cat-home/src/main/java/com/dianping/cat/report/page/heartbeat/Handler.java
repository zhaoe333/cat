package com.dianping.cat.report.page.heartbeat;

import java.io.IOException;
import java.util.Date;
import java.util.Set;

import javax.servlet.ServletException;

import org.unidal.lookup.annotation.Inject;
import org.unidal.web.mvc.PageHandler;
import org.unidal.web.mvc.annotation.InboundActionMeta;
import org.unidal.web.mvc.annotation.OutboundActionMeta;
import org.unidal.web.mvc.annotation.PayloadMeta;

import com.dianping.cat.Cat;
import com.dianping.cat.consumer.heartbeat.model.entity.HeartbeatReport;
import com.dianping.cat.helper.CatString;
import com.dianping.cat.helper.TimeUtil;
import com.dianping.cat.report.ReportPage;
import com.dianping.cat.report.graph.GraphBuilder;
import com.dianping.cat.report.page.NormalizePayload;
import com.dianping.cat.report.page.model.spi.ModelPeriod;
import com.dianping.cat.report.page.model.spi.ModelRequest;
import com.dianping.cat.report.page.model.spi.ModelResponse;
import com.dianping.cat.report.page.model.spi.ModelService;
import com.dianping.cat.report.service.ReportService;
import com.dianping.cat.report.view.StringSortHelper;
import com.google.gson.Gson;

public class Handler implements PageHandler<Context> {
	@Inject
	private GraphBuilder m_builder;

	@Inject
	private HistoryGraphs m_historyGraphs;

	@Inject
	private JspViewer m_jspViewer;

	@Inject
	private ReportService m_reportService;

	@Inject(type = ModelService.class, value = "heartbeat")
	private ModelService<HeartbeatReport> m_service;

	@Inject
	private NormalizePayload m_normalizePayload;

	private void buildHeartbeatGraphInfo(Model model, DisplayHeartbeat displayHeartbeat) {
		if (displayHeartbeat == null) {
			return;
		}
		model.setResult(displayHeartbeat);
		model.setActiveThreadGraph(displayHeartbeat.getActiceThreadGraph());
		model.setDaemonThreadGraph(displayHeartbeat.getDeamonThreadGraph());
		model.setTotalThreadGraph(displayHeartbeat.getTotalThreadGraph());
		model.setHttpThreadGraph(displayHeartbeat.getHttpTheadGraph());
		model.setStartedThreadGraph(displayHeartbeat.getStartedThreadGraph());
		model.setCatThreadGraph(displayHeartbeat.getCatThreadGraph());
		model.setPigeonThreadGraph(displayHeartbeat.getPigeonTheadGraph());
		model.setCatMessageProducedGraph(displayHeartbeat.getCatMessageProducedGraph());
		model.setCatMessageOverflowGraph(displayHeartbeat.getCatMessageOverflowGraph());
		model.setCatMessageSizeGraph(displayHeartbeat.getCatMessageSizeGraph());
		model.setNewGcCountGraph(displayHeartbeat.getNewGcCountGraph());
		model.setOldGcCountGraph(displayHeartbeat.getOldGcCountGraph());
		model.setHeapUsageGraph(displayHeartbeat.getHeapUsageGraph());
		model.setNoneHeapUsageGraph(displayHeartbeat.getNoneHeapUsageGraph());
		model.setDisks(displayHeartbeat.getDisks());
		model.setDisksGraph(displayHeartbeat.getDisksGraph());
		model.setSystemLoadAverageGraph(displayHeartbeat.getSystemLoadAverageGraph());
		model.setMemoryFreeGraph(displayHeartbeat.getMemoryFreeGraph());
	}

	private String getIpAddress(HeartbeatReport report, Payload payload) {
		Set<String> ips = report.getIps();
		String ip = payload.getIpAddress();

		if ((ip == null || ip.length() == 0) && !ips.isEmpty()) {
			ip = StringSortHelper.sort(ips).get(0);
		}

		return ip;
	}

	private HeartbeatReport getReport(Payload payload) {
		String domain = payload.getDomain();
		String date = String.valueOf(payload.getDate());
		ModelRequest request = new ModelRequest(domain, payload.getPeriod()) //
		      .setProperty("date", date).setProperty("ip", payload.getIpAddress());

		if (m_service.isEligable(request)) {
			ModelResponse<HeartbeatReport> response = m_service.invoke(request);
			HeartbeatReport report = response.getModel();

			if (payload.getPeriod().isLast()) {
				Set<String> domains = m_reportService.queryAllDomainNames(new Date(payload.getDate()),
				      new Date(payload.getDate() + TimeUtil.ONE_HOUR), "heartbeat");
				Set<String> domainNames = report.getDomainNames();

				domainNames.addAll(domains);
			}
			return report;
		} else {
			throw new RuntimeException("Internal error: no eligable ip service registered for " + request + "!");
		}
	}

	@Override
	@PayloadMeta(Payload.class)
	@InboundActionMeta(name = "h")
	public void handleInbound(Context ctx) throws ServletException, IOException {
		// display only, no action here
	}

	@Override
	@OutboundActionMeta(name = "h")
	public void handleOutbound(Context ctx) throws ServletException, IOException {
		Model model = new Model(ctx);
		Payload payload = ctx.getPayload();
		DisplayHeartbeat heartbeat = null;

		normalize(model, payload);
		switch (payload.getAction()) {
		case VIEW:
			heartbeat = showReport(model, payload);
			buildHeartbeatGraphInfo(model, heartbeat);
			break;
		case MOBILE:
			heartbeat = showReport(model, payload);
			MobileHeartbeat mobileModel = setMobileModel(model, heartbeat);
			String json = new Gson().toJson(mobileModel);

			model.setMobileResponse(json);
			break;
		case HISTORY:
			if (model.getIpAddress() != null) {
				m_historyGraphs.showHeartBeatGraph(model, payload);
			}
			break;
		case PART_HISTORY:
			if (model.getIpAddress() != null) {
				m_historyGraphs.showHeartBeatGraph(model, payload);
			}
			break;
		}
		m_jspViewer.view(ctx, model);
	}

	private void normalize(Model model, Payload payload) {
		model.setPage(ReportPage.HEARTBEAT);
		m_normalizePayload.normalize(model, payload);

		String queryType = payload.getType();

		if (queryType == null || queryType.trim().length() == 0) {
			payload.setType("frameworkThread");
		}
		if (CatString.ALL.equalsIgnoreCase(payload.getIpAddress())) {
			payload.setIpAddress("");
		}
	}

	private MobileHeartbeat setMobileModel(Model model, DisplayHeartbeat heartbeat) {
		MobileHeartbeat result = new MobileHeartbeat();
		result.display(model, heartbeat);
		return result;
	}

	private DisplayHeartbeat showReport(Model model, Payload payload) {
		try {
			ModelPeriod period = payload.getPeriod();

			if (period.isFuture()) {
				model.setLongDate(payload.getCurrentDate());
			} else {
				model.setLongDate(payload.getDate());
			}
			model.setDisplayDomain(payload.getDomain());

			HeartbeatReport report = getReport(payload);
			if (report == null) {
				return null;
			}
			model.setReport(report);
			String ip = getIpAddress(report, payload);
			model.setIpAddress(ip);

			DisplayHeartbeat displayHeartbeat = new DisplayHeartbeat(m_builder).display(report, ip);
			return displayHeartbeat;
		} catch (Throwable e) {
			Cat.logError(e);
			model.setException(e);
		}
		return null;
	}

	// the detail order of heartbeat is:name min max sum sum2 count_in_minutes
	public enum DetailOrder {
		NAME, MIN, MAX, SUM, SUM2, COUNT_IN_MINUTES
	}

}
