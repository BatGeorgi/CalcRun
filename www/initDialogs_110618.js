function endsWith(str, suff) {
	pos = str.length - suff.length;
	return str.indexOf(suff, pos) != -1;
}

function initDashboardDialogs() {
	$('#createDashboard').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Create": function () {
				cdName = $('#cdName').val();
				$.ajax({
					url: 'addDash',
					headers: {
						'Content-Type': 'application/json',
						'name': encodeURIComponent(cdName)
					},
					method: 'POST',
					dataType: 'text',
					statusCode: {
						200: function (resp) {
							initDashboards(true, true);
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
				$(this).dialog("close");
			},
			"Cancel": function () {
				$(this).dialog("close");
			}
		},
		width: 450,
		height: 200
	});
	$('#renameDashboard').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Rename": function () {
				var newName = $('#newDashName').val();
				$.ajax({
					url: 'renameDash',
					headers: {
						'Content-Type': 'application/json',
						'name': encodeURIComponent($('.selectedDash').attr('name')),
						'newName': encodeURIComponent(newName)
					},
					method: 'POST',
					dataType: 'text',
					statusCode: {
						200: function (resp) {
							initDashboards(true, true);
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
				$(this).dialog("close");
			},
			"Cancel": function () {
				$(this).dialog("close");
			}
		},
		width: 450,
		height: 200
	});
	$('#removeDashboard').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Remove": function () {
				$.ajax({
					url: 'removeDash',
					headers: {
						'Content-Type': 'application/json',
						'name': encodeURIComponent($('.selectedDash').attr('name'))
					},
					method: 'POST',
					dataType: 'text',
					statusCode: {
						200: function (resp) {
							initDashboards(true, true);
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
				$(this).dialog("close");
			},
			"Cancel": function () {
				$(this).dialog("close");
			}
		},
		width: 450,
		height: 200
	});
	$('#confirmDialogDash').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Yes": function () {
				elements = '';
				$("#dashSort li").each(function (idx, li) {
					elements += encodeURIComponent($(li).text()) + '|||';
				});
				$.ajax({
					url: 'saveDashOrder',
					headers: {
						'Content-Type': 'application/json',
						'elements': elements
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
				$(this).dialog("close");
			},
			"No": function () {
				$(this).dialog("close");
			}
		},
		width: 300,
		height: 200
	});
}

function initActionDialogs() {
	$('#editable').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Revert modifications": function () {
				$.ajax({
					url: 'revert',
					headers: {
						'Content-Type': 'application/json',
						'File': $('#editable').attr('genby')
					},
					method: 'POST',
					dataType: 'json',
					statusCode: {
						200: function (xhr) {
							$('#infoDialog').html('Activity modified!');
							$('#infoDialog').dialog('option', 'title', 'Status');
							$('#infoDialog').dialog('open');
							getBest();
							fetch(true);
						},
						401: function (xhr) {
							$('#infoDialog').html('Please sign in!');
							$('#infoDialog').dialog('option', 'title', 'Not authorized');
							$('#infoDialog').dialog('open');
						},
						400: function (xhr) {
							$('#infoDialog').html('Wrong data!');
							$('#infoDialog').dialog('option', 'title', 'Error');
							$('#infoDialog').dialog('open');
						}
					}
				});
				$(this).dialog("close");
			},
			"Modify": function () {
				dashAdd = encodeURIComponent($('#addToDash option:selected').val());
				dashRem = encodeURIComponent($('#remFromDash option:selected').val());
				changeDash('addToDash', $('#editable').attr('genby'), dashAdd);
				changeDash('removeFromDash', $('#editable').attr('genby'), dashRem);
				$.ajax({
					url: 'editActivity',
					headers: {
						'Content-Type': 'application/json',
						'File': $('#editable').attr('genby'),
						'Name': encodeURIComponent($('#chooseName').val()),
						'Type': $('#chooseType').val(),
						'actDist': $('#actDist').val(),
						'actTime': $('#actTime').val(),
						'actGain': $('#actGain').val(),
						'actLoss': $('#actLoss').val(),
						'garminLink': $('#chooseGarmin').val(),
						'ccLink': $('#chooseCC').val(),
						'photosLink': $('#choosePhotos').val(),
						'secure': $('#setProtected:checked').length > 0
					},
					method: 'POST',
					dataType: 'json',
					statusCode: {
						200: function (xhr) {
							$('#infoDialog').html('Activity Modified!');
							$('#infoDialog').dialog('option', 'title', 'Status');
							$('#infoDialog').dialog('open');
							getBest();
							fetch(true);
						},
						401: function (xhr) {
							$('#infoDialog').html('Please sign in!');
							$('#infoDialog').dialog('option', 'title', 'Not authorized');
							$('#infoDialog').dialog('open');
						},
						400: function (xhr) {
							$('#infoDialog').html('Wrong data!');
							$('#infoDialog').dialog('option', 'title', 'Error');
							$('#infoDialog').dialog('open');
						}
					}
				});
				$(this).dialog("close");
			},
			"Cancel": function () {
				$(this).dialog("close");
			}
		},
		width: Math.min($(window).width() * 0.7, 600),
		height: Math.min($(window).height() * 0.85, 750),
		create: function (event) {
			$(event.target).parent().css({
				'position': 'fixed',
				"left": 70,
				"top": 140
			});
		}
	});
	$('#removable').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Remove": function () {
				$.ajax({
					url: 'deleteActivity',
					headers: {
						'Content-Type': 'application/json',
						'File': $('#removable').attr('genby')
					},
					method: 'POST',
					dataType: 'json',
					statusCode: {
						200: function (xhr) {
							$('#infoDialog').html('Activity removed!');
							$('#infoDialog').dialog('option', 'title', 'Status');
							$('#infoDialog').dialog('open');
							getBest();
							fetch(true);
						},
						401: function (xhr) {
							$('#infoDialog').html('Please sign in!');
							$('#infoDialog').dialog('option', 'title', 'Not authorized');
							$('#infoDialog').dialog('open');
						},
						400: function (xhr) {
							$('#infoDialog').html('Name must not be empty!');
							$('#infoDialog').dialog('option', 'title', 'Error');
							$('#infoDialog').dialog('open');
						}
					}
				});
				$(this).dialog("close");
			},
			"Cancel": function () {
				$(this).dialog("close");
			}
		},
		width: Math.min($(window).width() * 0.4, 400),
		height: Math.min($(window).height() * 0.4, 300),
		create: function (event) {
			$(event.target).parent().css({
				'position': 'fixed',
				"left": 70,
				"top": 140
			});
		}
	});
	$('#featDialog').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Save": function () {
				feats = $(".featfield");
				flinks = [];
				$.each(feats, function (i, feat) {
					flinks.push(encodeURIComponent(feat.value));
				});
				$.ajax({
					url: 'setFeatures',
					headers: {
						'Content-Type': 'application/json',
						'descr': encodeURIComponent($('#featDescr').val()),
						'activity': $('#featDialog').attr('name'),
						'links': flinks
					},
					method: 'POST',
					dataType: 'json',
					statusCode: {
						200: function (xhr) {
							$('#infoDialog').html('Features set successfully!');
							$('#infoDialog').dialog('option', 'title', 'Status');
							$('#infoDialog').dialog('open');
							fetch(true);
						},
						401: function (xhr) {
							$('#infoDialog').html('Please sign in!');
							$('#infoDialog').dialog('option', 'title', 'Not authorized');
							$('#infoDialog').dialog('open');
						},
						400: function (xhr) {
							$('#infoDialog').html('Bad data format');
							$('#infoDialog').dialog('option', 'title', 'Error');
							$('#infoDialog').dialog('open');
						}
					}
				});
				$(this).dialog("close");
			},
			"Cancel": function () {
				$(this).dialog("close");
			}
		},
		width: Math.min($(window).width() * 0.8, 700),
		height: Math.min($(window).height() * 0.7, 600),
		create: function (event) {
			$(event.target).parent().css({
				'position': 'fixed',
				"left": 70,
				"top": 140
			});
		}
	});
	$('#comparable').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Compare": function () {
				$.ajax({
					url: 'compare',
					method: 'POST',
					dataType: 'json',
					headers: {
						'Content-Type': 'application/json',
						'file1': $('#comparable').attr('file1'),
						'file2': $('#selectComp').find(':selected').attr('genby')
					},
					statusCode: {
						200: function (data) {
							index = $('#selectComp option:selected').index() + 1;
							general = data['general'];
							times = data['times'];
							tt = '';
							$.each(times, function (i, sp) {
								tt += '<tr><td><strong>' + round(sp['point']) + '</strong></td><td>' + colorDiff1(sp['time1'], sp['time2']) + '</td><td>' + colorDiff1(sp['time2'], sp['time1']) + '</td><td>' + colorDiff(sp['currentDiff']) + '</td><td>' + colorDiff(sp['totalDiff']) + '</td></tr>';
							});
							$('#selectComp :nth-child(' + index + ')').prop('selected', true);
							f1 = $('#comparable').attr('file1');
							if (endsWith(f1, ".gpx")) {
								f1 = f1.substring(0, f1.length - 4);
							}
							f2 = $('#selectComp').find(':selected').attr('genby');
							if (endsWith(f2, ".gpx")) {
								f2 = f2.substring(0, f2.length - 4);
							}
							compLink = 'compare?a1=' + f1 + '&a2=' + f2;
							var extLinkComp = '<hr><input class="hovs hovsMain" title="View full comparison" type="image" src="extview-icon.png" width="60" height="60" onclick="window.open(\'' + compLink + '\', \'_blank\');return false;" />';
							$('#compareResults').html(extLinkComp + '<hr><table class="highlightOnly"><thead><th>Stat</th><th>' + decodeURIComponent(general['name1']) + ' ' + general['date1'] + '</th><th>' + decodeURIComponent(general['name2']) + ' ' + general['date2'] + '</th></thead><tbody>' +
								'<tr><td>Date</td><td>' + general['date1'] + '</td><td>' + general['date2'] + '</td></tr><tr><td>Distance</td><td>' + comp2(general['dist1'], general['dist2']) + '</td><td>' + comp2(general['dist2'], general['dist1']) + '</td></tr>' +
								'<tr><td>Time</td><td>' + colorDiff1(general['time1'], general['time2']) + '</td><td>' + colorDiff1(general['time2'], general['time1']) + '</td></tr>' +
								'<tr><td>Speed</td><td>' + comp2(general['speed1'], general['speed2']) + '</td><td>' + comp2(general['speed2'], general['speed1']) + '</td></tr><tr><td>Elev gain</td><td>' + general['elePos1'] + '</td><td>' + general['elePos2'] + '</td></tr>' +
								'<tr><td>Elev loss</td><td>' + general['eleNeg1'] + '</td><td>' + general['eleNeg2'] + '</td></tr>' +
								'<tr><td>Highly intensive period</td><td>' + colorDiff1(general['timeRunning1'], general['timeRunning2']) + '</td><td>' + colorDiff1(general['timeRunning2'], general['timeRunning1']) + '</td></tr>' +
								'<tr><td>Highly intensive period distance</td><td>' + comp2(general['distRunning1'], general['distRunning2']) + '</td><td>' + comp2(general['distRunning2'], general['distRunning1']) + '</td></tr>' +
								'<tr><td>Highly intensive period elev gain</td><td>' + compGr(general['eleRunningPos1'], general['eleRunningPos2']) + '</td><td>' + compGr(general['eleRunningPos2'], general['eleRunningPos1']) + '</td></tr>' +
								'<tr><td>Highly intensive period elev loss</td><td>' + general['eleRunningNeg1'] + '</td><td>' + general['eleRunningNeg2'] + '</td></tr>' + '</tbody></table>');
							$('#compareResults').append('<span class="highlight"><h2>Splits</h2><table><thead><th>KM</th><th>' + decodeURIComponent(general['name1']) + ' ' + general['date1'] + '</th><th>' + decodeURIComponent(general['name2']) + ' ' + general['date2'] + '</th><th>Segment diff</th><th>Total diff</th></thead><tbody>' + tt + '</tbody></table></span>');
						},
						400: function (xhr) {
							$('#infoDialog').html('Activities may be removed :(');
							$('#infoDialog').dialog('option', 'title', 'Error');
							$('#infoDialog').dialog('open');
						}
					}
				});
			},
			"Close": function () {
				$(this).dialog("close");
			}
		},
		width: Math.min(800, $(window).width() * 0.8),
		height: Math.min(800, $(window).height() * 0.8),
		create: function (event) {
			$(event.target).parent().css({
				'position': 'fixed',
				"left": 50,
				"top": 150
			});
		}
	});
}

function initAggregationDialogs() {
	$('#bestSplits').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Close": function () {
				$(this).dialog("close");
			}
		},
		width: Math.min($(window).width() * 0.85, 850),
		height: Math.min($(window).height() * 0.85, 800),
		create: function (event) {
			$(event.target).parent().css({
				'position': 'fixed',
				"left": 50,
				"top": 150
			});
		}
	});
	$('#mTotals').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Close": function () {
				$(this).dialog("close");
			}
		},
		width: Math.min(800, $(window).width() * 0.8),
		height: Math.min($(window).height() * 0.85, 900),
		create: function (event) {
			$(event.target).parent().css({
				'position': 'fixed',
				"left": 50,
				"top": 150
			});
		}
	});
	$('#wTotals').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Close": function () {
				$(this).dialog("close");
			}
		},
		width: Math.min(800, $(window).width() * 0.8),
		height: Math.min($(window).height() * 0.85, 900),
		create: function (event) {
			$(event.target).parent().css({
				'position': 'fixed',
				"left": 50,
				"top": 150
			});
		}
	});
	$('#typesDialog').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Close": function () {
				$(this).dialog("close");
			}
		},
		width: Math.min(900, $(window).width() * 0.85),
		height: Math.min($(window).height() * 0.85, 900),
		create: function (event) {
			$(event.target).parent().css({
				'position': 'fixed',
				"left": 50,
				"top": 150
			});
		}
	});
}

function initPresetDialogs() {
	$('#confirmDialogPresets').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Yes": function () {
				elements = '';
				$("#sortable li").each(function (idx, li) {
					elements += $(li).text() + '|||';
				});
				$.ajax({
					url: 'savePresetOrder',
					headers: {
						'Content-Type': 'application/json',
						'elements': elements
					},
					method: 'POST',
					dataType: 'text',
					statusCode: {
						200: function (resp) {
							$('#infoDialog').html(resp);
							$('#infoDialog').dialog('option', 'title', 'Status');
							$('#infoDialog').dialog('open');
							$("#sortable li").removeClass('defPreset');
							$("#sortable li").first().addClass('defPreset');
						},
						401: function (resp) {
							$('#infoDialog').html('Please sign in!');
							$('#infoDialog').dialog('option', 'title', 'Not authorized');
							$('#infoDialog').dialog('open');
						}
					}
				});
				$(this).dialog("close");
			},
			"No": function () {
				$(this).dialog("close");
			}
		},
		width: 300,
		height: 200
	});
	$('#presetsDlg').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Save": function () {
				name = $('#newPresetName').val();
				period = $('input[name=selectPeriod]:checked').attr('id');
				ds = $('#dtStart').datepicker('getDate');
				de = $('#dtEnd').datepicker('getDate');
				nf = encodeURIComponent($('#searchName').val());
				if (!validatePresetName(name)) {
					$('#infoDialog').html('Preset name must only contain latin letters, digits and _');
					$('#infoDialog').dialog('option', 'title', 'Status');
					$('#infoDialog').dialog('open');
					$(this).dialog("close");
					return;
				}
				$.ajax({
					url: 'saveFilter',
					headers: {
						'Content-Type': 'application/json',
						'name': name,
						'run': $('#checkbox-1').prop('checked'),
						'trail': $('#checkbox-2').prop('checked'),
						'uphill': $('#checkbox-3').prop('checked'),
						'hike': $('#checkbox-4').prop('checked'),
						'walk': $('#checkbox-5').prop('checked'),
						'other': $('#checkbox-6').prop('checked'),
						'dateOpt': parseInt(period[period.length - 1]) - 1,
						'startDate': ds ? (ds.getMonth() + 1) + '/' + ds.getDate() + '/' + ds.getFullYear() : '',
						'endDate': de ? (de.getMonth() + 1) + '/' + de.getDate() + '/' + de.getFullYear() : '',
						'pattern': nf,
						'dmin': $('#spinnerMin').val(),
						'dmax': $('#spinnerMax').val(),
						'dashboard': encodeURIComponent($('.selectedDash').attr('name'))
					},
					method: 'POST',
					dataType: 'text',
					statusCode: {
						200: function (resp) {
							$('#infoDialog').html(resp);
							$('#infoDialog').dialog('option', 'title', 'Status');
							$('#infoDialog').dialog('open');
							getPresets();
						},
						401: function (resp) {
							$('#infoDialog').html('Please sign in!');
							$('#infoDialog').dialog('option', 'title', 'Not authorized');
							$('#infoDialog').dialog('open');
						}
					}
				});
				$(this).dialog("close");
			},
			"Cancel": function () {
				$(this).dialog("close");
			}
		},
		width: 450,
		height: 200
	});
	$('#renamePresetDlg').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Rename": function () {
				$.ajax({
					url: 'renameFilter',
					headers: {
						'Content-Type': 'application/json',
						'name': $('#selectPreset').find(':selected').val(),
						'newName': $('#setPresetName').val()
					},
					method: 'POST',
					dataType: 'text',
					statusCode: {
						200: function (resp) {
							$('#infoDialog').html(resp);
							$('#infoDialog').dialog('option', 'title', 'Status');
							$('#infoDialog').dialog('open');
							getPresets();
						},
						401: function (resp) {
							$('#infoDialog').html('Please sign in!');
							$('#infoDialog').dialog('option', 'title', 'Not authorized');
							$('#infoDialog').dialog('open');
						}
					}
				});
				$(this).dialog("close");
			},
			"Cancel": function () {
				$(this).dialog("close");
			}
		},
		width: 450,
		height: 250
	});
	$('#removePresetDlg').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Remove": function () {
				$.ajax({
					url: 'removeFilter',
					headers: {
						'Content-Type': 'application/json',
						'name': $('#selectPresetRem').find(':selected').val()
					},
					method: 'POST',
					dataType: 'text',
					statusCode: {
						200: function (resp) {
							$('#infoDialog').html(resp);
							$('#infoDialog').dialog('option', 'title', 'Status');
							$('#infoDialog').dialog('open');
							getPresets();
						},
						401: function (resp) {
							$('#infoDialog').html('Please sign in!');
							$('#infoDialog').dialog('option', 'title', 'Not authorized');
							$('#infoDialog').dialog('open');
						}
					}
				});
				$(this).dialog("close");
			},
			"Cancel": function () {
				$(this).dialog("close");
			}
		},
		width: 450,
		height: 250
	});
}

function initControlDialogs() {
	$('#infoDialog').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"OK": function () {
				$(this).dialog("close");
			}
		},
		width: 300,
		height: 200
	});
	$('#login').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Login": function () {
				$.ajax({
					url: 'login',
					headers: {
						'Content-Type': 'application/json',
						'User': $('#loginUser').val(),
						'Password': $('#loginPassword').val()
					},
					method: 'POST',
					dataType: 'json',
					statusCode: {
						200: function (xhr) {
							$('#infoDialog').html('Logged in!');
							$('#infoDialog').dialog('option', 'title', 'Status');
							$('#infoDialog').dialog('open');
							$('#loginButton').text("Logout");
							fetch(true);
						},
						401: function (xhr) {
							$('#infoDialog').html('Please sign in!');
							$('#infoDialog').dialog('option', 'title', 'Not authorized');
							$('#infoDialog').dialog('open');
						},
						500: function (xhr) {
							$('#infoDialog').html('Internal server error :(');
							$('#infoDialog').dialog('option', 'title', 'Error');
							$('#infoDialog').dialog('open');
						}
					}
				});
				$(this).dialog("close");
			},
			"Cancel": function () {
				$(this).dialog("close");
			}
		},
		width: 450,
		height: 250
	});
	$('#logout').dialog({
		autoOpen: false,
		modal: true,
		show: "blind",
		hide: "blind",
		buttons: {
			"Logout": function () {
				$.ajax({
					url: 'removeCookie',
					headers: {
						'Content-Type': 'application/txt'
					},
					method: 'POST',
					dataType: 'json',
					statusCode: {
						200: function (xhr) {
							document.cookie = 'xruncalc=; expires=Thu, 01 Jan 1970 00:00:01 GMT;';
							$('#infoDialog').html('Logged out');
							$('#infoDialog').dialog('option', 'title', 'Status');
							$('#infoDialog').dialog('open');
							$('#loginButton').text("Login");
							fetch(true);
						},
						401: function (xhr) {
							$('#infoDialog').html('Not logged in!');
							$('#infoDialog').dialog('option', 'title', 'Not authorized');
							$('#infoDialog').dialog('open');
						},
						500: function (xhr) {
							$('#infoDialog').html('Internal server error :(');
							$('#infoDialog').dialog('option', 'title', 'Error');
							$('#infoDialog').dialog('open');
						}
					}
				});
				$(this).dialog("close");
			},
			"Cancel": function () {
				$(this).dialog("close");
			}
		},
		width: 250,
		height: 150
	});
}

function initAboutDialog() {
	$('#aboutDialog').dialog({
		autoOpen: false,
		modal: false,
		show: "blind",
		hide: "blind",
		buttons: {
			"Close": function () {
				$(this).dialog("close");
			}
		},
		width: 450,
		height: 400
	});
}

function initDialogs() {
	initDashboardDialogs();
	initActionDialogs();
	initAggregationDialogs();
	initPresetDialogs();
	initControlDialogs();
	initControlDialogs();
	initAboutDialog();
}
