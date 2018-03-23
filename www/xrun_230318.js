var compactDate = false;

function compFast(file2) {
	$.ajax({
		url: 'compare',
		method: 'POST',
		dataType: 'json',
		headers: {
			'Content-Type': 'application/json',
			'file1': $('#comparable').attr('file1'),
			'file2': file2
		},
		statusCode: {
			200: function (data) {
				general = data['general'];
				times = data['times'];
				tt = '';
				$.each(times, function (i, sp) {
					tt += '<tr><td><strong>' + round(sp['point']) + '</strong></td><td>' + colorDiff1(sp['time1'], sp['time2']) + '</td><td>' + colorDiff1(sp['time2'], sp['time1']) + '</td><td>' + colorDiff(sp['currentDiff']) + '</td><td>' + colorDiff(sp['totalDiff']) + '</td></tr>';
				});
				f1 = $('#comparable').attr('file1');
				if (endsWith(f1, ".gpx")) {
					f1 = f1.substring(0, f1.length - 4);
				}
				if (endsWith(file2, ".gpx")) {
					file2 = file2.substring(0, file2.length - 4);
				}
				compLink = 'compare?a1=' + f1 + '&a2=' + file2;
				var extLinkComp = '<hr><input class="hovs" title="View full comparison" type="image" src="extview-icon.png" width="60" height="60" onclick="window.open(\'' + compLink + '\', \'_blank\');return false;" />';
				$('#compareResults').html(extLinkComp + '<hr><table class="highlightOnly"><thead><th>Stat</th><th>' + decodeURIComponent(general['name1']) + ' ' + general['date1'] + '</th><th>' + decodeURIComponent(general['name2']) + ' ' + general['date2'] + '</th></thead><tbody>' +
					'<tr><td>Date</td><td>' + general['date1'] + '</td><td>' + general['date2'] + '</td></tr><tr><td>Distance</td><td>' + comp2(general['dist1'], general['dist2']) + '</td><td>' + comp2(general['dist2'], general['dist1']) + '</td></tr>' +
					'<tr><td>Time</td><td>' + colorDiff1(general['time1'], general['time2']) + '</td><td>' + colorDiff1(general['time2'], general['time1']) + '</td></tr>' +
					'<tr><td>Speed</td><td>' + comp2(general['speed1'], general['speed2']) + '</td><td>' + comp2(general['speed2'], general['speed1']) + '</td></tr><tr><td>Elev gain</td><td>' + general['elePos1'] + '</td><td>' + general['elePos2'] + '</td></tr>' +
					'<tr><td>Elev loss</td><td>' + general['eleNeg1'] + '</td><td>' + general['eleNeg2'] + '</td></tr>' +
					'<tr><td>Running|>9km/h| time</td><td>' + colorDiff1(general['timeRunning1'], general['timeRunning2']) + '</td><td>' + colorDiff1(general['timeRunning2'], general['timeRunning1']) + '</td></tr>' +
					'<tr><td>Running|>9km/h| distance</td><td>' + comp2(general['distRunning1'], general['distRunning2']) + '</td><td>' + comp2(general['distRunning2'], general['distRunning1']) + '</td></tr>' +
					'<tr><td>Running|>9km/h| elev gain</td><td>' + compGr(general['eleRunningPos1'], general['eleRunningPos2']) + '</td><td>' + compGr(general['eleRunningPos2'], general['eleRunningPos1']) + '</td></tr>' +
					'<tr><td>Running|>9km/h| elev loss</td><td>' + general['eleRunningNeg1'] + '</td><td>' + general['eleRunningNeg2'] + '</td></tr>' + '</tbody></table>');
				$('#compareResults').append('<span class="highlight"><h2>Splits</h2><table><thead><th>Point(km)</th><th>' + decodeURIComponent(general['name1']) + ' ' + general['date1'] + '</th><th>' + decodeURIComponent(general['name2']) + ' ' + general['date2'] + '</th><th>Segment diff</th><th>Total diff</th></thead><tbody>' + tt + '</tbody></table></span>');
			},
			400: function (xhr) {
				$('#infoDialog').html('Activities may be removed :(');
				$('#infoDialog').dialog('option', 'title', 'Error');
				$('#infoDialog').dialog('open');
			}
		}
	});
}

function initPortableDialogs() {
	$('#overall').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Close": function () {
				$(this).dialog("close");
			}
		},
		width: Math.min(400),
		height: Math.min(450),
		create: function (event) {
			$(event.target).parent().css({
				'position': 'fixed',
				"left": 50,
				"top": 150
			});
		}
	});
	$('#bestOf').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Close": function () {
				$(this).dialog("close");
			}
		},
		width: Math.min(400),
		height: Math.min(600),
		create: function (event) {
			$(event.target).parent().css({
				'position': 'fixed',
				"left": 50,
				"top": 150
			});
		}
	});
}

function vald(ch, allowWh) {
	if (ch == ' ') {
		return allowWh;
	}
	if (ch == '_') {
		return true;
	}
	if (ch >= 'a' && ch <= 'z') {
		return true;
	}
	if (ch >= 'A' && ch <= 'Z') {
		return true;
	}
	if (ch >= '0' && ch <= '9') {
		return true;
	}
	return false;
}

function validateDashName(dash) {
	for (i = 0; i < dash.length; ++i) {
		if (!vald(dash.charAt(i), true)) {
			return false;
		}
	}
	return true;
}

function validatePresetName(preset) {
	for (i = 0; i < preset.length; ++i) {
		if (!vald(preset.charAt(i), false)) {
			return false;
		}
	}
	return true;
}

function getOption(val, current) {
	return '<option value="' + val + '"' + (current == val ? ' selected>' : '>') + val + '</option>';
}

function colorDiff(diff) {
	c = diff.charAt(0);
	if (c == '+') {
		return '<span class="red">' + diff + '</span>';
	}
	if (c == '-') {
		return '<span class="green">' + diff + '</span>';
	}
	return diff;
}

function valTime(time) {
	res = time.split(":");
	if (res.length == 2) {
		return 60 * parseInt(res[0]) + parseInt(res[1]);
	}
	return 3600 * parseInt(res[0]) + 60 * parseInt(res[1]) + parseInt(res[2]);
}

function comp(t1, t2, tt) {
	if (t1 < t2) {
		return '<span class="green">' + tt + '</span>';
	}
	if (t1 == t2) {
		return tt;
	}
	return '<span class="red">' + tt + '</span>';
}

function compGr(t1, t2) {
	if (t1 > t2) {
		return '<span class="green">' + t1 + '</span>';
	}
	if (t1 == t2) {
		return t1;
	}
	return '<span class="red">' + t1 + '</span>';
}

function comp2(t1, t2) {
	return comp(parseFloat(t2.replace(',', '.')), parseFloat(t1.replace(',', '.')), t1);
}

function colorDiff1(tt1, tt2) {
	t1 = valTime(tt1);
	t2 = valTime(tt2);
	return comp(t1, t2, tt1);
}

function formatEle(gain, loss) {
	res = '-';
	if (gain > 0 && loss > 0) {
		res = '<span class="green">+' + gain + '</span>/<span class="red">-' + loss + '</span>';
	} else if (gain > 0) {
		res = '<span class="green">+' + gain + '</span>';
	} else if (loss > 0) {
		res = '<span class="red">-' + loss + '</span>';
	}
	return res;
}

function formatEleDiff(diff) {
	if (diff == 0) {
		return '0';
	}
	if (diff > 0) {
		return '<span class="green">+' + diff + '</span>';
	}
	return '<span class="red">' + diff + '</span>';
}

function round(point) {
	if (Math.abs(point - Math.round(point)) < 1e-3) {
		return Math.round(point);
	}
	return point;
}

function changeDash(alias, activity, dashboard) {
	if (dashboard == '') {
		return;
	}
	$.ajax({
		url: alias,
		headers: {
			'Content-Type': 'application/txt',
			'activity': activity,
			'dashboard': dashboard,
		},
		method: 'POST',
		dataType: 'text',
		statusCode: {
			200: function (resp) {
				$('#infoDialog').html(resp);
				$('#infoDialog').dialog('option', 'title', 'Status');
				$('#infoDialog').dialog('open');
			},
			401: function (resp) {
				$('#infoDialog').html('Please sign in!');
				$('#infoDialog').dialog('option', 'title', 'Not authorized');
				$('#infoDialog').dialog('open');
			}
		}
	});
}

function initPresets(presets) {
	allPresetsHtml = '<ul id="sortable">';
	$.each(presets, function (i, item) {
		name = decodeURIComponent(item['name']);
		allPresetsHtml += '<li class="presetButton" id="preset' + name + '">' + name + '</li>';
	});
	$('#allPresets').html(allPresetsHtml + '</ul>');
	$("#sortable").sortable();
	$("#sortable").disableSelection();
	$("#dashSort").sortable();
	$("#dashSort").disableSelection();
	$.each(presets, function (i, item) {
		name = decodeURIComponent(item['name']);
		$('#preset' + name).click(function () {
			if ($('#search').button("option", "disabled")) {
				return;
			}
			$(this).addClass('selectedDash');
			$('#searchName').val(decodeURIComponent(item['pattern']));
			for (i = 1; i < 7; ++i) {
				$('#checkbox-' + i).prop('checked', false).change();
			}
			if (item['run']) {
				$('#checkbox-1').prop('checked', true).change();
			}
			if (item['trail']) {
				$('#checkbox-2').prop('checked', true).change();
			}
			if (item['uphill']) {
				$('#checkbox-3').prop('checked', true).change();
			}
			if (item['hike']) {
				$('#checkbox-4').prop('checked', true).change();
			}
			if (item['walk']) {
				$('#checkbox-5').prop('checked', true).change();
			}
			if (item['other']) {
				$('#checkbox-6').prop('checked', true).change();
			}
			sd = item['startDate'];
			if (sd.length > 0) {
				si = sd.indexOf('/');
				if (si == -1) {
					$('#radio-' + (parseInt(sd) + 1)).prop('checked', true).change();
				} else {
					$('#radio-7').prop('checked', true).change();
					$('#dtStart').val(sd);
				}
			} else {
				$('#dtEnd').val('');
			}
			$('#dtEnd').val(item['endDate']);
			if (item['minDist'] > 0) {
				$('#spinnerMin').val(item['minDist']);
			} else {
				$('#spinnerMin').val('');
			}
			if (item['maxDist'] < 2147483647) {
				$('#spinnerMax').val(item['maxDist']);
			} else {
				$('#spinnerMax').val('');
			}
			fetchAfterDashClick = false;
			$('li[name="' + decodeURIComponent(item['dashboard']) + '"]').click();
			fetchAfterDashClick = true;
			$('#runs').hide();
			$('#ht').html('<div class="loader"></div>');
			$('#runs').trigger('pagerUpdate', 1);
			$('#runs').trigger('pageSize', 20);
			$('a').removeClass('current');
			$('#defPage').addClass('current');
			$('#pagerMenu').hide();
			fetch(false);
		});
	});
}

function getPresets() {
	$.ajax({
		url: 'getFilters',
		method: 'POST',
		dataType: 'json',
		headers: {
			'Content-Type': 'application/json'
		},
		statusCode: {
			200: function (data) {
				initPresets(data['presets']);
			}
		}
	});
}

function initFeatures() {
	$("#addLink").click(function () {
		lastField = $("#buildlinks div:last");
		intId = (lastField && lastField.length && lastField.data("idx") + 1) || 1;
		fieldWrapper = $('<div class="fieldwrapper" id="field' + intId + '"/>');
		fieldWrapper.data("idx", intId);
		fName = $('<input type="text" class="featfield" />');
		fLink = $('<input type="text" class="featfield" />');
		removeButton = $('<input type="button" class="remove" value="Remove" />');
		removeButton.click(function () {
			$(this).parent().remove();
		});
		fieldWrapper.append('<b>Name</b> ');
		fieldWrapper.append(fName);
		fieldWrapper.append(' <b>Link</b> ');
		fieldWrapper.append(fLink);
		fieldWrapper.append(removeButton);
		$("#buildlinks").append(fieldWrapper);
	});
}

function endsWith(str, suff) {
	pos = str.length - suff.length;
	return str.indexOf(suff, pos) != -1;
}

function initContent(data) {
	itemsDS = [];
	$('#dataHolder').html('');
	all = data['activities'];
	if (all.length == 0) {
		flt = data['filter'].replace('Activities', 'Looking for activities ');
		$('#ht').html('No activities found<p>' + flt);
		$('#runs tbody').html('');
		$('#runs').trigger('update');
		$('#runs').tablesorter();
		$('#runs').hide();
		$('#overall').hide();
		$('#overallLoader').html('');
		return false;
	}
	$('#overall').show();
	$('#overallLoader').html('');
	$('#runs').show();
	$('#pagerMenu').show();
	if ("Activities" !== data['filter']) {
		$('#ht').html(data['filter'] + '<p>');
	} else {
		$('#ht').html('');
	}
	averageData = '';
	if (data['avgDaily'] != '') {
		averageData = '<tr><td>Average daily</td><td>' + data['avgDaily'] + ' km</td></tr>';
	}
	if (data['avgWeekly'] != '') {
		averageData += '<tr><td>Average weekly</td><td>' + data['avgWeekly'] + ' km</td></tr>';
	}
	if (data['avgMonthly'] != '') {
		averageData += '<tr><td>Average monthly</td><td>' + data['avgMonthly'] + ' km</td></tr>';
	}
	$('#overall tbody').html('<tr><td>Distance</td><td>' + data['totalDistance'] + " km</td><tr><td>Time</td><td>" + data['totalTime'] + '</td><tr><td>Average speed</td><td>' + data['avgSpeed'] + ' km/h</td></tr>' +
		'<tr><td>Average distance</td><td>' + data['avgDist'] + ' km</td></tr>' + averageData +
		'<tr><td>Elevation gain</td><td>' + data['elePos'] + ' m</td></tr><tr><td>Elevation loss</td><td>' + data['eleNeg'] + ' m</td></tr><tr><td>Running distance(>9km/h)</td><td>' + data['totalRunDist'] + ' km</td></tr>');
	if (data['WMT'] != 'none') {
		initMonthlyTotals(data['mtotals']);
		initWeeklyTotals(data['wtotals']);
	}
	runsHtml = '';
	optsAll = '';
	var dialog = {
		autoOpen: false,
		modal: false,
		show: "blind",
		hide: "blind",
		buttons: {
			"Close": function () {
				$(this).dialog("close");
			}
		},
		width: Math.min($(window).width() * 0.7, 600),
		height: Math.min($(window).height() * 0.85, 800),
		create: function (event) {
			$(event.target).parent().css({
				'position': 'fixed',
				"left": 50,
				"top": 150
			});
		}
	};
	var crun = 0;
	var ctrail = 0;
	var cup = 0;
	var chike = 0;
	var cwalk = 0;
	var coth = 0;
	isAllTrail = data['isAllTrail'];
	if (isAllTrail) {
		$('#runs tr:eq(0) th:eq(7)').html("RunDist&nbsp;&nbsp;");
	} else {
		$('table tr:eq(0) th:eq(7)').html("Speed&nbsp;&nbsp;");
	}
	$.each(all, function (i, item) {
		var isMod = item['isModified'] == 'y';
		dateStr = item['date'];
		if (compactDate) {
			dateStr = item['day'] + '.' + (item['month'] + 1) + '.' + (item['year'] - 2000);
		}
		runsHtml += '<tr><td>' + (i + 1) + '</td><td><div id="date' + i + '">' + dateStr + '</div></td><td><div title="View details" class="runitem" id="item' + i + '">' + (isMod ? '<i>' : '') + decodeURIComponent(item['name']) + (isMod ? '</i>' : '') +
			'</div></td><td><div id="type' + i + '">' + item['type'] + '</div></td><td>' +
			item['dist'] + '</td><td>' + item['timeTotal'] + '</td><td>' + item['avgPace'] + '</td><td>' + (isAllTrail ? item['distRunning'] : item['avgSpeed']) + '</td>' +
			'<td>' + formatEle(item['eleTotalPos'], item['eleTotalNeg']) + '</td>' +
			'<td><div id="edit' + i + '" class="ui-icon ui-icon-pencil ui-state-hover runitem" title="Edit activity data"></div>' +
			'<div id="feat' + i + '" class="ui-icon ui-icon-note ui-state-hover runitem" title="Set description and links"></div>' +
			'<div id="compare' + i + '" class="ui-icon ui-icon-arrowthick-2-e-w ui-state-hover runitem" title="Compare with another activity"></div>' +
			'<div id="trash' + i + '" class="ui-icon ui-icon-trash ui-state-hover runitem" title="Remove activity"></div></td></tr>';
		$('#dataHolder').append('<div style="display: none;" id="data' + i + '"></div>');
		$('#dataHolder').append('<div style="display: none;" id="ui' + i + '"></div>');
		filename = endsWith(item['genby'], '.gpx') ? item['genby'].substring(0, item['genby'].length - 4) : item['genby'];
		itemsDS.push(function () {
			$('#ui' + i).dialog(dialog);
			$('#ui' + i).html($('#data' + i).html());
			$('#ui' + i).dialog('option', 'title', 'Details ' + $('#item' + i).text());
			$('#ui' + i).dialog('open');
			$.ajax({
					url: 'getSplitsAndDist',
					headers: {
						'Content-Type': 'application/json',
						'activity': item['genby']
					},
					method: 'POST',
					dataType: 'json',
					statusCode: {
						200: function (data) {
							distr = data['speedDist'];
							tableHtml = '<hr><li><h3>Speed distribution</h3><span class="highlight"><table><thead><th>Range</th><th>Time</th><th>Distance</th><th>Gain</th><th>Loss</th></thead><tbody>';
							$.each(distr, function (w, diap) {
								tableHtml += '<tr><td><strong>' + diap['range'] + ' km/h</strong></td><td>' + diap['time'] + '</td><td>' + diap['dist'] + '</td><td><span class="green">' + diap['elePos'] + '</span></td><td><span class="red">' + diap['eleNeg'] + '</span></td></tr>';
							});
							tableHtml += '</tbody></table></span>';
							splitHtml = '<hr><li><h3>Splits</h3><span class="highlight"><table><thead><th>Point(km)</th><th>Pace</th><th>Avg speed</th><th>Diff</th><th>Total time</th><th>Acc speed</th></thead><tbody>';
							splits = data['splits'];
							$.each(splits, function (v, sp) {
								splitHtml += '<tr><td><strong>' + round(sp['total']) + '</strong></td><td>' + sp['pace'] + '</td><td>' + sp['speed'] + '</td><td>' + formatEleDiff(sp['ele']) + '</td><td>' + sp['timeTotal'] +
								'</td><td>' + sp['accumSpeed'] + '</td></tr>';
							});
							splitHtml += '</tbody></table></span>';
							$('#ui' + i).append('<ul><li>' + tableHtml + '</li><li>' + splitHtml + '</li></ul>');
						},
						400: function (xhr) {
							$('#ui' + i).append('Error retrieving data :(');
						}
					}
			});
		});
		var origData = item['origData'];
		var extLinks = '<input class="hovs" title="View full info" type="image" src="extview-icon.png" width="60" height="60" onclick="window.open(\'' + filename + '\', \'_blank\');return false;" />';
		if (item['garminLink'] != 'none') {
			extLinks += '<input class="hovs" title="View on Garmin Connect" type="image" src="garmin-icon.png" width="60" height="60" onclick="window.open(\'' + item['garminLink'] + '\', \'_blank\');return false;" />';
		}
		if (item['ccLink'] != 'none') {
			ccl = decodeURIComponent(item['ccLink']);
			extLinks += '<input class="hovs" title="View on ReliveCC" type="image" src="relivecc-icon.png" width="60" height="60" onclick="window.open(\'' + ccl + '\', \'_blank\');return false;" />';
		}
		if (item['photosLink'] != 'none') {
			phl = decodeURIComponent(item['photosLink']);
			extLinks += '<input class="hovs" title="View photos" type="image" src="photos-icon.png" width="60" height="60" onclick="window.open(\'' + phl + '\', \'_blank\');return false;" />';
		}
		tags = $.parseJSON(item['dashboards']);
		dashboardTags = '<span class="dashboardList">';
		$.each(tags, function (d, itemd) {
			if (d > 0) {
				dashboardTags += ', ';
			}
			dashboardTags += decodeURIComponent(itemd);
		});
		dashboardTags += '</span><hr>';
		descrHtml = '';
		if (item['descr'].length > 0) {
			descrHtml = '<blockquote>' + decodeURIComponent(item['descr']) + '</blockquote><hr>';
		}
		ilinks = $.parseJSON(item['links']);
		linksHtml = '';
		if (ilinks.length > 0) {
			for (e = 0; e < ilinks.length; e += 2) {
				targ = decodeURIComponent(ilinks[e + 1]);
				if (targ.indexOf("youtube") != -1 || targ.indexOf("youtu.be") != -1) {
					linksHtml += '<image src="images/youtube.png"/>';
				}
				linksHtml += '<a href="' + decodeURIComponent(ilinks[e + 1]) + '" target="_blank">' + decodeURIComponent(ilinks[e]) + '</a><p>';
			}
			linksHtml += '<hr>';
		}
		$('#data' + i).append(extLinks + '<hr>' + dashboardTags + descrHtml + linksHtml + '<ul><li></li>' + '<table><tbody><tr><td>Date</td><td>' + item['startAt'] + '</td></tr><tr><td>Distance</td><td>' +
			item['dist'] + (isMod ? '<em> / ' + origData['dist'] + '*</em>' : '') + '</td></tr><tr><td>Total time</td><td>' + item['timeTotal'] + (isMod ? '<em> / ' + origData['timeTotal'] + '*</em>' : '') +
			'</td></tr><tr><td>Elevation gain</td><td>' +
			'<span class="green">' + item['eleTotalPos'] + (isMod ? '<em> / ' + origData['eleTotalPos'] + '*</em>' : '') + '</span></td></tr><tr><td>Elevation loss</td><td>' + '<span class="red">' +
			item['eleTotalNeg'] + (isMod ? '<em> / ' + origData['eleTotalNeg'] + '*</em>' : '') + '</span></td></tr><tr><td>' +
			'Running|>9km/h| time</td><td>' + (isMod ? '<em>' : '') + item['timeRunning'] + (isMod ? '*</em>' : '') + '</td></tr><tr><td>Running|>9km/h| distance</td><td>' + (isMod ? '<em>' : '') +
			item['distRunning'] + (isMod ? '*</em>' : '') + '</td></tr><tr><td>Running|>9km/h| elevation gain</td><td>' +
			'<span class="green">' + (isMod ? '<em>' : '') + item['eleRunningPos'] + (isMod ? '*</em>' : '') + '</span></td></tr><tr><td>Running|>9km/h| elevation loss</td><td>' +
			'<span class="red">' + (isMod ? '<em>' : '') + item['eleRunningNeg'] + (isMod ? '*</em>' : '') + '</span></td></tr><tr><td>Rest time</td><td>' + (isMod ? '<em>' : '') + item['timeRest'] + (isMod ? '*</em>' : '') +
			'</td></tr><tr><td>Average speed</td><td>' + item['avgSpeed'] + (isMod ? '<em> / ' + origData['avgSpeed'] + '*</em>' : '') +
			'</td></tr><tr><td>Average pace</td><td>' + item['avgPace'] + (isMod ? '<em> / ' + origData['avgPace'] + '*</em>' : '') + '</td></tr></tbody></table></ul>');
	});
	var happc = '<div id="typesDistr" title="View results distribution">' + all.length + (all.length != 1 ? ' results' : ' result') + '</div>';
	$('#ht').append(happc);
	charts = data['charts'];
	$('#typesDistr').click(function () {
		$('#typesDialog').html('<strong>Count by</strong><p><fieldset><input type="radio" name="selectChart" value="byType" checked="">Type' +
			'<input type="radio" name="selectChart" value="byDist">Distance' +
			'<input type="radio" name="selectChart" value="bySpeed">Speed' +
			'<input type="radio" name="selectChart" value="byEle">Denivelation+' +
			'<input type="radio" name="selectChart" value="byRun">Running distance' +
			'<input type="radio" name="selectChart" value="byDuration">Duration' +
			'<input type="radio" name="selectChart" value="byMonth">Month' +
			'<input type="radio" name="selectChart" value="byYear">Year' +
			'<input type="radio" name="selectChart" value="byDay">Day' +
			'<input type="radio" name="selectChart" value="byHour">Hour</fieldset><hr>' +
			'<div id="byType"></div><div id="byDist"></div><div id="bySpeed"></div><div id="byEle"></div><div id="byRun"></div>' +
			'<div id="byDuration"></div><div id="byMonth"></div><div id="byYear"></div><div id="byDay"></div><div id="byHour"></div>');
		$('#byType').insertFusionCharts(getChart("Distribution", "By type", "Type", "Count", "", getChartData(charts['byType'])));
		$('#byDist').insertFusionCharts(getChart("Distribution", "By distance", "KM", "Count", "", getChartData(charts['byDist'])));
		$('#bySpeed').insertFusionCharts(getChart("Distribution", "By speed", "KM/H", "Count", "", getChartData(charts['bySpeed'])));
		$('#byEle').insertFusionCharts(getChart("Distribution", "By positive denivelation", "Meters gained", "Count", "", getChartData(charts['byEle'])));
		$('#byRun').insertFusionCharts(getChart("Distribution", "By running distance", "KM", "Count", "", getChartData(charts['byRun'])));
		$('#byDuration').insertFusionCharts(getChart("Distribution", "By duration", "Time", "Count", "", getChartData(charts['byDuration'])));
		$('#byMonth').insertFusionCharts(getChart("Distribution", "By month", "Month", "Count", "", getChartData(charts['byMonth'])));
		$('#byYear').insertFusionCharts(getChart("Distribution", "By year", "Year", "Count", "", getChartData(charts['byYear'])));
		$('#byDay').insertFusionCharts(getChart("Distribution", "By day of week", "Day of week", "Count", "", getChartData(charts['byDay'])));
		$('#byHour').insertFusionCharts(getChart("Distribution", "By starting hour", "Period of day", "Count", "", getChartData(charts['byHour'])));
		$('input[name=selectChart]').change(function () {
			chid = $('input[name=selectChart]:checked').val();
			$("div[id^=by]").hide();
			$('#' + chid).show();
		});
		$("div[id^=by]").hide();
		$('#byType').show();
		$('#typesDialog').dialog('option', 'title', 'Results');
		$('#typesDialog').dialog('open');
	});
	$('#runs tbody').html(runsHtml);
	$('#runs').trigger('update');
	$('#runs').trigger('pageSize', 20);
	$('#runs').trigger("sorton", [
		[
			[1, 'dates']
		]
	]);
	var table = document.getElementById('runs');
	var rowLength = table.rows.length;
	for (var i = 1; i < rowLength; i++) {
		table.rows[i].cells[0].innerHTML = (i).toString();
	}
	$.each(all, function (i, item) {
		optsAll += '<option genby="' + item['genby'] + '" value="#' + (i + 1) + ' ' + $('#item' + i).text() + '">' + $('#item' + i).text() + ' ' + $('#date' + i).text() + '</option>';
	});
	$.each(all, function (i, item) {
		$('#compare' + i).click(function () {
			$('#comparable').html('Compare with <select id="selectComp">' + optsAll + '</select><div id="comparePre" /><div id="compareExt" /><div id="compareResults" />');
			$('#comparable').dialog('option', 'title', 'Compare ' + $('#item' + i).text() + ' ' + $('#date' + i).text() + ', ' + item['dist'] + "km");
			$('#comparable').attr('file1', item['genby']);
			$('#comparable').dialog('open');
			$.ajax({
					url: 'getCompOptions',
					method: 'POST',
					dataType: 'json',
					headers: {
						'Content-Type': 'application/json',
						'activity': item['genby']
					},
					statusCode: {
						200: function (data) {
							comps = data['comps'];
							hotOpts = '<hr><p><ul class="highlight">';
							$.each(comps, function (h, xitem) {
								hotOpts += '<li><div class="hovs runitem" id="compOpt' + h + '">' + decodeURIComponent(xitem['text']) + '</div></li>';
							});
							$('#comparePre').html(hotOpts + '</ul>');
							$.each(comps, function (h, xitem) {
								$('#compOpt' + h).click(function () {
									compFast(xitem['id']);
								});
							});
						}
					}
			});
			$.ajax({
				url: 'getCompOptions',
				method: 'POST',
				dataType: 'json',
				headers: {
					'Content-Type': 'application/json',
					'activity': item['genby'],
					'ext': true
				},
				statusCode: {
					200: function (data) {
						comps = data['comps'];
						if (comps.length == 0) {
							return;
						}
						extCompsDiv = '<p><div id="extComps"></div>';
						extOpts = '<p><h3>External</h3><ul class="highlight">';
						$.each(comps, function (h, xitem) {
							extOpts += '<li><div class="hovs runitem" id="compExt' + h + '">' + decodeURIComponent(xitem['text']) + '</div></li>';
						});
						extOpts += '</ul>';
						$('#compareExt').html('<p><button id="extCompSwitch">Show external</button>' + extCompsDiv);
						$('#extComps').hide();
						$('#extComps').html(extOpts);
						$('#extCompSwitch').click(function() {
							if ($('#extComps').css('display') == 'none') {
								$(this).text("Hide external");
							} else {
								$(this).text("Show external");
							}
							$('#extComps').toggle();
						});
						$.each(comps, function (h, xitem) {
							$('#compExt' + h).click(function () {
								compFast(xitem['id']);
							});
						});
					},
					401: function(data) {
						$('#compareExt').html("<h3>Log in to view external options</h3>");
					}
				}
			});
		});
		$('#feat' + i).click(function () {
			ilinks = $.parseJSON(item['links']);
			flinkHtml = '';
			for (d = 0; d < ilinks.length; d += 2) {
				linkInd = parseInt(d / 2);
				flinkHtml += '<div class="fieldwrapper" id="field' + linkInd + '"><b>Name</b> <input type="text" class="featfield" value="' + decodeURIComponent(ilinks[d]) + '"/>' +
					' <b>Link</b> <input type="text" class="featfield" value="' + decodeURIComponent(ilinks[d + 1]) + '"/><input type="button" class="remove" value="Remove"/></div>';
			}
			$('#featDialog').attr('name', item['genby']);
			$('#featDialog').html('<h3>Description</h3><textarea id="featDescr" row="10" cols="50">' + decodeURIComponent(item['descr']) + '</textarea><p><h3>Media</h3>' +
				'<fieldset id="buildlinks"><legend>Links</legend>' + flinkHtml + '</fieldset>' +
				'<p><input type="button" value="Add" class="add" id="addLink" />');
			$('.remove').click(function () {
				$(this).parent().remove();
			});
			$('#featDialog').dialog('option', 'title', 'Edit ' + $('#item' + i).text() + ' ' + $('#date' + i).text() + " Features");
			$('#featDialog').attr('file', item['genby']);
			$('#featDialog').dialog('open');
			initFeatures();
		});
		$('#edit' + i).click(function () {
			type = $('#type' + i).text();
			optsAdd = '<option value="">---Keep---</option>';
			optsRem = '<option value="">---Keep---</option>';
			dashboards = item['dashboards'];
			for (q = 0; q < dashCount; ++q) {
				name = $('#dashboard' + q).attr('name');
				if (dashboards.indexOf(encodeURIComponent(name)) == -1) {
					optsAdd += '<option value="' + name + '">' + name + '</option>';
				} else {
					optsRem += '<option value="' + name + '">' + name + '</option>';
				}
			}
			$('#editable').html('<table><tbody><tr><td>Name </td><td><input type="text" id="chooseName" value="' + $('#item' + i).text() + '"/></td></tr><tr><td>Type </td><td><select id="chooseType">' + getOption("Running", type) +
				getOption("Trail", type) + getOption("Uphill", type) + getOption('Hiking', type) + getOption('Walking', type) + getOption('Other', type) + '</select></td>' +
				'<tr><td>Garmin </td><td><input size="50" type="text" id="chooseGarmin" value="' + (item['garminLink'] != 'none' ? item['garminLink'] : "") + '"/></td></tr>' +
				'<tr><td>Relive CC </td><td><input size="50" type="text" id="chooseCC" value="' + (item['ccLink'] != 'none' ? decodeURIComponent(item['ccLink']) : "") + '"/></td></tr>' +
				'<tr><td>Photos </td><td><input size="50" type="text" id="choosePhotos" value="' + (item['photosLink'] != 'none' ? decodeURIComponent(item['photosLink']) : "") + '"/></td></tr>' +
				'<tr><td>Add to dashboard</td><td><select id="addToDash">' + optsAdd + '</select></td></tr>' +
				'<tr><td>Remove from dashboard</td><td><select id="remFromDash">' + optsRem + '</select></td></tr></tbody></table>' +
				'<u><h2>Modifications</h2></u><p><h4>Optional. Leave empty to keep old data.</h4>' +
				'<table><tbody><tr><td>Actual distance</td><td><input type="text" id="actDist" /></td></tr>' +
				'<tr><td>Actual time(hh:mm:ss)</td><td><input type="text" id="actTime" /></td></tr>' +
				'<tr><td>Actual elevation gain</td><td><input type="text" id="actGain" /></td></tr>' +
				'<tr><td>Actual elevation loss</td><td><input type="text" id="actLoss" /></td></tr>' + '</tbody></table>');
			$('#editable').dialog('option', 'title', 'Edit ' + $('#item' + i).text() + ' ' + $('#date' + i).text());
			$('#editable').attr('genby', item['genby']);
			$('#editable').attr('refname', 'item' + i);
			$('#editable').attr('reftype', 'type' + i);
			$('#editable').dialog('open');
		});
		$('#trash' + i).click(function () {
			$('#removable').html('Remove this activity?');
			$('#removable').dialog('option', 'title', 'Remove ' + $('#item' + i).text() + ' ' + $('#date' + i).text());
			$('#removable').attr('genby', item['genby']);
			$('#removable').dialog('open');
		});
	});
	for (i = 0; i < itemsDS.length; ++i) {
		$('#runs tr:eq(' + (i + 1) + ') td:not(:last-child)').click(itemsDS[i]);
	}
}

function fetch(getWMTotals) {
	var period = $('input[name=selectPeriod]:checked').attr('id');
	var ds = $('#dtStart').datepicker('getDate');
	var de = $('#dtEnd').datepicker('getDate');
	var nf = encodeURIComponent($('#searchName').val());
	$('#search').button("option", "disabled", true);
	$('#overall').hide();
	if ($('#overallBtn').length == 0) {
		$('#overallLoader').html('<div class="loader"></div>');
	}
	$('#runs').hide();
	$('#ht').html('<div class="loader"></div>');
	$('#pagerMenu').hide();
	dashQ = $('.selectedDash').length == 0 ? 'Main' : encodeURIComponent($('.selectedDash').attr('name'));
	$.ajax({
		url: 'fetch',
		method: 'POST',
		dataType: 'json',
		headers: {
			'Content-Type': 'application/json',
			'run': $('#checkbox-1').prop('checked'),
			'trail': $('#checkbox-2').prop('checked'),
			'uphill': $('#checkbox-3').prop('checked'),
			'hike': $('#checkbox-4').prop('checked'),
			'walk': $('#checkbox-5').prop('checked'),
			'other': $('#checkbox-6').prop('checked'),
			'dateOpt': parseInt(period[period.length - 1]) - 1,
			'dtStart': ds ? (ds.getMonth() + 1) + '/' + ds.getDate() + '/' + ds.getFullYear() : '',
			'dtEnd': de ? (de.getMonth() + 1) + '/' + de.getDate() + '/' + de.getFullYear() : '',
			'nameFilter': nf,
			'dmin': $('#spinnerMin').val(),
			'dmax': $('#spinnerMax').val(),
			'dashboard': dashQ,
			'getWMTotals': getWMTotals
		},
		statusCode: {
			200: function (data) {
				$(".presetButton").removeClass('selectedDash');
				initContent(data);
				$('#search').button("option", "disabled", false);
				for (i = 0; i < dashCount; ++i) {
					$('#dashboard' + i).prop("disabled", false);
				}
			},
			400: function (data) {
				$(".presetButton").removeClass('selectedDash');
				$('#infoDialog').html('Invalid distance arguments!');
				$('#infoDialog').dialog('option', 'title', 'Bad data');
				$('#infoDialog').dialog('open');
				$('#search').button("option", "disabled", false);
				for (i = 0; i < dashCount; ++i) {
					$('#dashboard' + i).prop("disabled", false);
				}
				initContent({'activities': [], 'filter': ''});
			},
			401: function (data) {
				$(".presetButton").removeClass('selectedDash');
				$('#infoDialog').html('Must be logged in to view this dashboard');
				$('#infoDialog').dialog('option', 'title', 'Not authorized');
				$('#infoDialog').dialog('open');
				$('#search').button("option", "disabled", false);
				for (i = 0; i < dashCount; ++i) {
					$('#dashboard' + i).prop("disabled", false);
				}
				initContent({'activities': [], 'filter': ''});
			}
		}
	});
}

function disableDates() {
	$('#dtStart').datepicker('disable');
	$('#dtEnd').datepicker('disable');
}

function getStat(data, opt) {
	if (data[opt]) {
		return data[opt];
	}
	return opt == 'ach' ? 'Locked' : '---';
}

function linkAchToActivity(ind, data, opt) {
	if (data[opt]) {
		$('#bestOf tr:eq(' + ind + ')').click(function () {
			window.open(data['genby'], '_blank');
		});
	} else {
		$('#bestOf tr:eq(' + ind + ')').click(function () {
			$('#infoDialog').html('Achievement not unlocked!');
			$('#infoDialog').dialog('option', 'title', 'No data');
			$('#infoDialog').dialog('open');
		});
	}
}

function getBest() {
	initSplitsBest();
	$('#bestOf').hide();
	if ($('#bestOfBtn').length == 0) {
		$('#bestOfLoader').html('<div class="loader"></div>');
	}
	$.ajax({
		url: 'best',
		method: 'POST',
		dataType: 'json',
		headers: {
			'Content-Type': 'application/json'
		},
		statusCode: {
			200: function (data) {
				var farthest = data['longest'];
				var fastest = data['fastest'];
				var maxAsc = data['maxAsc'];
				var maxRun = data['maxRun'];
				var k1 = data['1K'];
				var k25 = data['2K5'];
				var k5 = data['5K'];
				var k10 = data['10K'];
				var semi = data['21K'];
				var k30 = data['30K'];
				var marathon = data['42K'];
				$('#bestOf tbody').html('<tr><td>Farthest</td><td>' + getStat(farthest, 'ach') + '</td><td>' + getStat(farthest, 'when') + '</td></tr>' +
					'<tr><td>Fastest</td><td>' + getStat(fastest, 'ach') + '</td><td>' + getStat(fastest, 'when') + '</td></tr>' +
					'<tr><td>Max ascent</td><td>' + getStat(maxAsc, 'ach') + '</td><td>' + getStat(maxAsc, 'when') + '</td></tr>' +
					'<tr><td>Max running distance</td><td>' + getStat(maxRun, 'ach') + '</td><td>' + getStat(maxRun, 'when') + '</td></tr>' +
					'<tr><td>1K</td><td>' + getStat(k1, 'ach') + '</td><td>' + getStat(k1, 'when') + '</td></tr>' +
					'<tr><td>2.5K</td><td>' + getStat(k25, 'ach') + '</td><td>' + getStat(k25, 'when') + '</td></tr>' +
					'<tr><td>5K</td><td>' + getStat(k5, 'ach') + '</td><td>' + getStat(k5, 'when') + '</td></tr>' +
					'<tr><td>10K</td><td>' + getStat(k10, 'ach') + '</td><td>' + getStat(k10, 'when') + '</td></tr>' +
					'<tr><td>Half-marathon</td><td>' + getStat(semi, 'ach') + '</td><td>' + getStat(semi, 'when') + '</td></tr>' +
					'<tr><td>30K</td><td>' + getStat(k30, 'ach') + '</td><td>' + getStat(k30, 'when') + '</td></tr>' +
					'<tr><td>Marathon</td><td>' + getStat(marathon, 'ach') + '</td><td>' + getStat(marathon, 'when') + '</td></tr>');
				$('#bestOf').show();
				$('#bestOfLoader').html('');
				linkAchToActivity(0, farthest, 'ach');
				linkAchToActivity(1, fastest, 'ach');
				linkAchToActivity(2, maxAsc, 'ach');
				linkAchToActivity(3, maxRun, 'ach');
				linkAchToActivity(4, k1, 'ach');
				linkAchToActivity(5, k25, 'ach');
				linkAchToActivity(6, k5, 'ach');
				linkAchToActivity(7, k10, 'ach');
				linkAchToActivity(8, semi, 'ach');
				linkAchToActivity(9, k30, 'ach');
				linkAchToActivity(10, marathon, 'ach');
			}
		}
	});
}

function initSplitsBest() {
	$.ajax({
		url: 'bestSplits',
		method: 'POST',
		dataType: 'json',
		headers: {
			'Content-Type': 'application/json'
		},
		statusCode: {
			200: function (data) {
				$('#bestSplits').dialog('option', 'title', 'Best split times dy distance');
				totals = data['totals'];
				splits = '';
				$.each(totals, function (i, item) {
					splits += '<tr><td><strong>' + item['point'] + '</strong></td><td>' + item['ach'] + '</td><td class="runitem">' + decodeURIComponent(item['name']) + '</td><td>' + item['date'] + '</td>' +
						'<td>' + item['pace'] + '</td><td>' + item['speed'] + '</td></tr>';
				});
				$('#bestSplits').html('<span class="highlight"><table id="bestSplitsTable"><thead><tr><th>Distance</th><th>Best time</th><th>Name</th><th>Date</th>' +
					'<th>Pace</th><th>Speed</th></tr></thead><tbody>' + splits + '</tbody></table></span>');
				$.each(totals, function (i, item) {
					$('#bestSplitsTable tr:eq(' + (i + 1) + ')').click(function () {
						window.open(item['genby'], '_blank');
					});
				});
			}
		}
	});
}

function getChart(caption, subcap, xname, yname, suff, data, id) {
	chart = getChartFromId(id);
	if (chart !== undefined) {
		chart.dispose();
	}
	return {
		type: "column2d",
		width: "700",
		height: "300",
		dataFormat: "json",
		id: id,
		dataSource: {
			"chart": {
				"caption": caption,
				"subCaption": subcap,
				"xAxisName": xname,
				"yAxisName": yname,
				"exportEnabled": "1",
				"numberSuffix": suff,
				"theme": "fint"
			},
			data: data
		}
	}
}

function getChartData(chart) {
	result = [];
	$.each(chart, function (i, item) {
		result.push({
			"label": item['info'].toString(),
			"value": item['data']
		});
	});
	return result;
}

function getYearData(data, type) {
	result = [];
	$.each(data, function (i, item) {
		mdata = item['data'];
		ckey = 'count' + type;
		cval = mdata.hasOwnProperty(ckey) ? '(' + mdata[ckey] + ')' : '';
		result.push({
			"label": item['month'] + cval,
			"value": parseFloat(mdata[type]).toFixed(1)
		});
	});
	return result;
}

function genContainerHTML(year) {
	return '<div id="mtContainerRT' + year + '"></div>' + '<div id="mtContainerR' + year + '"></div>' + '<div id="mtContainerT' + year + '"></div>' +
		'<div id="mtContainerU' + year + '"></div>' + '<div id="mtContainerH' + year + '"></div>' + '<div id="mtContainerD' + year + '"></div>' +
		'<div id="mtContainerA' + year + '"></div>';
}

function gcn(type, weekInd) {
	return 'wContainer' + weekInd + type;
}

function gcnDiv(type, weekInd) {
	return '<div id="' + gcn(type, weekInd) + '"></div>';
}

function genContainerHTML2(weekInd) {
	return gcnDiv('rt', weekInd) + gcnDiv('r', weekInd) + gcnDiv('t', weekInd) + gcnDiv('u', weekInd) + gcnDiv('h', weekInd) + gcnDiv('d', weekInd) + gcnDiv('a', weekInd);
}

function showContainer(type) {
	$("div[id^=mtContainer]").hide();
	$("div[id^=mtContainer" + type + "2]").show();
}

function genRadioM(type, name, checked) {
	ch = (checked ? ' checked' : '');
	return '<input type="radio" name="selectTypeM" value="' + type + '" ' + ch + '>' + name + '</input>';
}

function initMonthlyTotals(data) {
	$('#mTotals').dialog('option', 'title', 'Total distance by month');
	radioHtml = '<strong>Choose type</strong><p><fieldset>' + genRadioM('RT', 'Run&Trail', true) + genRadioM('R', 'Run') + genRadioM('T', 'Trail') + genRadioM('U', 'Uphill') +
		genRadioM('H', 'Hike') + genRadioM('D', 'Denivelation+') + genRadioM('A', 'All') + '</fieldset>';
	containerHtml = '';
	$.each(data, function (i, item) {
		containerHtml += genContainerHTML(item['year']);
	});
	$('#mTotals').html(radioHtml + '<hr/>' + containerHtml);
	$("div[id^=mtContainer]").hide();
	$.each(data, function (i, item) {
		ydata = item['data'];
		year = item['year'];
		$("#mtContainerRT" + year).insertFusionCharts(getChart("Running&Trail " + item['year'], "Year monthly report", "Month", "Distance", "km", getYearData(ydata, 'rt')));
		$("#mtContainerR" + year).insertFusionCharts(getChart("Running " + item['year'], "Year monthly report", "Month", "Distance", "km", getYearData(ydata, 'r')));
		$("#mtContainerT" + year).insertFusionCharts(getChart("Trail running " + item['year'], "Year monthly report", "Month", "Distance", "km", getYearData(ydata, 't')));
		$("#mtContainerU" + year).insertFusionCharts(getChart("Uphill climbing " + item['year'], "Year monthly report", "Month", "Distance", "km", getYearData(ydata, 'u')));
		$("#mtContainerH" + year).insertFusionCharts(getChart("Hiking " + item['year'], "Year monthly report", "Month", "Distance", "km", getYearData(ydata, 'h')));
		$("#mtContainerD" + year).insertFusionCharts(getChart("Positive denivelation " + item['year'], "Year monthly report", "Month", "Denivelation", "m", getYearData(ydata, 'totalPositiveEl')));
		$("#mtContainerA" + year).insertFusionCharts(getChart("All activities " + item['year'], "Year monthly report", "Month", "Distance", "km", getYearData(ydata, 'a')));
		$('input[name=selectTypeM]').change(function () {
			showContainer($('input[name=selectTypeM]:checked').val());
		});
		if (i == 0) {
			showContainer("RT");
		}
	});
}

function genRadioT(type, name, checked) {
	ch = (checked ? ' checked' : '');
	return '<input type="radio" name="selectTypeW" value="' + type + '"' + ch + ' hrn="' + name + '">' + name + '</input>';
}

function genRadioYear(year, ind, checked) {
	ch = (checked ? ' checked' : '');
	return '<input type="radio" name="selectYearW" value="' + year + '" ind="' + ind + '" ' + ch + '>' + year + '</input>';
}

function getWeekData(data, type, fr, to) {
	result = [];
	$.each(data, function (i, item) {
		if (i >= fr && i < to) {
			ckey = 'count' + type;
			cval = item.hasOwnProperty(ckey) ? '(' + item[ckey] + ')' : '';
			info = item['info'];
			if (item.hasOwnProperty(ckey) && item[ckey] == 0) {
				cval = '';
				info = 'Empty Week';
			}
			result.push({
				"label": info + cval,
				"value": parseFloat(item[type]).toFixed(1)
			});
		}
	});
	return result;
}

var wtotals = [];
var currentYearInd = 0;

function initWeeklyTotals(data) {
	$('#wTotals').dialog('option', 'title', 'Total distance by week');
	radioHtml = '<strong>Choose type</strong><p><fieldset>' + genRadioT('rt', 'Run&Trail', true) + genRadioT('r', 'Run') + genRadioT('t', 'Trail') + genRadioT('u', 'Uphill') +
		genRadioT('h', 'Hike') + genRadioT('d', 'Denivelation+') + genRadioT('a', 'All') + '</fieldset>';
	menuHtml = '<strong>Choose year</strong>';
	yearSelectHtml = '<fieldset>';
	containerHtml = '';
	$.each(data, function (i, item) {
		yearSelectHtml += genRadioYear(item['year'], i, i == 0);
	});
	yearSelectHtml += '</fieldset>';
	for (j = 1; j <= 4; ++j) {
		containerHtml += genContainerHTML2(j);
	}
	$('#wTotals').html(radioHtml + '<p>' + menuHtml + '<p>' + yearSelectHtml + '<hr>' + containerHtml);
	wtotals = data;
	$.each(wtotals, function (i, item) {
		ydata = item['data'];
		year = item['year'];
		f = function () {
			$('div[id^="wContainer"]').html('');
			currentYearInd = parseInt($('input[name=selectYearW]:checked').attr('ind'));
			for (j = 1; j <= 4; ++j) {
				fr = 13 * (j - 1);
				to = (j < 4 ? 13 * j : 100);
				optType = $('input[name=selectTypeW]:checked').val();
				optText = $('input[name=selectTypeW]:checked').attr('hrn') + ' ' + wtotals[currentYearInd]['year'];
				ftype = optType !== 'd' ? optType : 'totalPositiveEl';
				wd = getWeekData(wtotals[currentYearInd]['data'], ftype, fr, to);
				meas = optType !== 'd' ? 'km' : 'm';
				yax = optType !== 'd' ? 'Distance' : 'Denivelation';
				if (wd.length > 0) {
					$('#' + gcn(optType, j)).insertFusionCharts(getChart(optText, "Year weekly report", "Week", yax, meas, wd, 'wid' + optType + j));
				}
			}
		};
		$('input[name=selectTypeW],[name=selectYearW]').change(f);
		if (i == 0) {
			f.call();
		}
	});
}

var dashCount = 0;
var fetchAfterDashClick = true;

function initDashboards(fetchAfterInit) {
	$.ajax({
		url: 'getDash',
		method: 'POST',
		dataType: 'json',
		statusCode: {
			200: function (data) {
				arr = data['dashboards'];
				dashHtml = '<ul class="xdash">';
				dashCount = arr.length;
				$.each(arr, function (i, item) {
					dashHtml += '<li class="dashButton" id="dashboard' + i + '">' + decodeURIComponent(item) + '</li>';
					if (i == 0 && dashCount > 1) {
						dashHtml += '</ul><ul id="dashSort" class="xdash">';
					}
				});
				$('#allDash').html(dashHtml + '</ul>');
				$.each(arr, function (i, item) {
					if (item == 'Main') {
						$('#dashboard' + i).addClass('selectedDash');
					} else {
						$('#dashboard' + i).addClass('notSelectedDash');
					}
					$('#dashboard' + i).attr('name', decodeURIComponent(item));
					$('#dashboard' + i).click(function () {
						if ($('#dashboard' + i).hasClass('selectedDash')) {
							return;
						}
						if ($('#search').button("option", "disabled")) {
							return;
						}
						$('#dashboard' + i).addClass('selectedDash');
						$('#dashboard' + i).removeClass('notSelectedDash');
						for (j = 0; j < dashCount; ++j) {
							if (j != i) {
								$('#dashboard' + j).prop("disabled", true);
								$('#dashboard' + j).addClass('notSelectedDash');
								$('#dashboard' + j).removeClass('selectedDash');
							}
						}
						$('#runs').hide();
						$('#ht').html('<div class="loader"></div>');
						$('#pagerMenu').hide();
						$('#runs').trigger('pagerUpdate', 1);
						$('#runs').trigger('pageSize', 20);
						$('a').removeClass('current');
						$('#defPage').addClass('current');
						if (fetchAfterDashClick) {
							$('#checkbox-1').prop('checked', true).change();
							$('#checkbox-2').prop('checked', true).change();
							$('#checkbox-3').prop('checked', true).change();
							$('#checkbox-4').prop('checked', true).change();
							$('#checkbox-5').prop('checked', false).change();
							$('#checkbox-6').prop('checked', false).change();
							$('#radio-6').prop('checked', true).change();
							$('#spinnerMin').val('');
							$('#spinnerMax').val('');
							$('#searchName').val('');
							fetch(false);
						}
					});
				});
				if (fetchAfterInit) {
					fetch(false);
				}
			}
		}
	});
}

function checkWidth() {
	wid = $(window).width();
	if (wid < 1200) {
		initPortableDialogs();
		$('#bestOfWrap').append('<button id="bestOfBtn">Best achievements</button>');
		$('#bestOfBtn').button();
		$('#overallWrap').append('<button id="overallBtn">Selection totals</button>');
		$('#overallBtn').button();
		$('#bestOf').dialog('option', 'title', 'Best achievements');
		$('#overall').dialog('option', 'title', 'Selection totals');
		$('#bestOfBtn').click(function () {
			$('#bestOf').dialog('open');
		});
		$('#overallBtn').click(function () {
			$('#overall').dialog('open');
		});
		$('#titleTotals').remove();
		$('#titleBest').remove();
	}
	if (wid < 1800) {
		compactDate = true;
		if (wid >= 1600) {
			$('#runs').css({
				'width': '85%'
			});
		}
	}
	if (wid >= 1400 && wid < 1600) {
		$('#runs').css({
			'width': '90%'
		});
	}
	if (wid < 1400) {
		$('#runs').css({
			'width': '95%'
		});
	}
}