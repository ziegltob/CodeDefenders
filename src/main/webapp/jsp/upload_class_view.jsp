<%@ page import="org.codedefenders.database.DatabaseAccess" %>
<%@ page import="org.codedefenders.database.FeedbackDAO" %>
<%@ page import="org.codedefenders.database.GameClassDAO" %>
<%@ page import="org.codedefenders.game.GameClass" %>
<%@ page import="java.util.List" %>
<% String pageTitle=null; %>
<%@ include file="/jsp/header_main.jsp" %>
<div>
	<div class="w-100 up">
		<h2>Upload Class</h2>
		<div id="divUpload" >
			<form id="formUpload" action="<%=request.getContextPath() %>/upload" class="form-upload" method="post" enctype="multipart/form-data">
				<span class="label label-danger" id="invalid_alias" style="color: white;visibility: hidden">Name with no whitespaces or special characters.</span>
				<input id="classAlias" onkeyup="validateAlias()" name="classAlias" type="text" class="form-control" placeholder="Optional class alias, otherwise class name is used" >
				<!--
				<input type="checkbox" name="prepareForSingle" value="prepare" style="margin-right:5px;">Generate mutants and tests for single-player mode? (It may take a while...)</input>
				-->
				<div>
                    <h3>Upload Class under Test</h3>
                    <span class="file-select">
                        <input id="fileUploadCUT" name="fileUploadCUT" type="file" class="file-loading" accept=".java" required />
                    </span>
					<br>
					<span>The class used for games. Mutants are created from and tests are created for this class.</span>
				</div>
				<div>
					<h3>Upload Dependencies (optional)</h3>
					<span class="file-select">
                        <input id="fileUploadDependency" name="fileUploadDependency" type="file" class="file-loading" accept=".zip" />
                    </span>
					<br>
					<span>
                        If the class under test has dependencies, upload them as inside a <code>zip</code> file.
					</span>
				</div>
				<div>
					<h3>Upload Mutants (optional)</h3>
					<span class="file-select">
                        <input id="fileUploadMutant" name="fileUploadMutant" type="file" class="file-loading" accept=".zip" />
                    </span>
					<br>
                    <span>
                        Mutants uploaded with a class under test can be used to initialize games with existing mutants.
                        Note that all mutants must have the same class name as the class under test and must be uploaded inside a <code>zip</code> file.
					</span>
				</div>
				<div>
					<h3>Upload Tests (optional)</h3>
					<span class="file-select">
                        <input id="fileUploadTest" name="fileUploadTest" type="file" class="file-loading" accept=".zip" />
                    </span>
					<br>
                    <span>
                        Tests uploaded with a class under test can be used to initialize games with existing tests.
                        Note that all tests must be uploaded inside a <code>zip</code> file.
                    </span>
				</div>
				<br>
                <input id="mockingEnabled" type="checkbox" name="enableMocking" value="isMocking" style="margin-right:5px;">Enable Mocking for this class</input>
				<br>
				<span class="submit-button">
					<input id="upload" type="submit" class="fileinput-upload-button" value="Upload" onClick="this.form.submit(); this.disabled=true; this.value='Uploading...';"/>
				</span>

				<input type="hidden" value="<%=request.getParameter("fromAdmin")%>" name="fromAdmin">
				<script>
                    function validateAlias() {
                        let classAlias = document.forms["formUpload"]["classAlias"].value;

                        let regExp = new RegExp('^[a-zA-Z0-9]*$');
                        if (regExp.test(classAlias)) {
                            document.getElementById('invalid_alias').style.visibility = "hidden";
                            document.getElementById('upload').disabled = false;
                        } else {
                            document.getElementById('invalid_alias').style.visibility = "visible";
                            document.getElementById('upload').disabled = true;
                        }
                    }
				</script>
			</form>
		</div>
	</div>
	<div class="w-100">
		<h2>Uploaded Classes</h2>
		<!-- Deactivated because single player mode is not activated currently
		<span>Preparing classes for the single player mode (action 'Prepare AI') may take a long time.</span>
		-->
		<div id="classList" >
			<%
				List<GameClass> gameClasses = DatabaseAccess.getAllClasses();
				List<Double> avgMutationDifficulties = FeedbackDAO.getAverageMutationDifficulties();
				List<Double> avgTestDifficulties = FeedbackDAO.getAverageTestDifficulties();
			%>
			<table class="table table-striped table-hover table-responsive table-paragraphs games-table" id = "tableUploadedClasses">
				<thead>
				<tr>
					<th class="col-sm-1 col-sm-offset-2">ID</th>
					<th>Class name (alias)</th>
					<th>Available dependencies/tests/mutants</th>
					<th>Mutation Difficulty</th>
					<th>Testing Difficulty</th>
				</tr>
				</thead>
				<tbody>
				<% if (gameClasses.isEmpty()) { %>
					<tr><td colspan="100%">No classes uploaded.</td></tr>
				<% } else {
					for (int i = 0; i < gameClasses.size(); i++) {
						GameClass c = gameClasses.get(i);
						double mutationDiff = avgMutationDifficulties.get(i);
						double testingDiff = avgTestDifficulties.get(i);
					%>
						<tr>
							<td><%= c.getId() %></td>
							<td>
								<a href="#" data-toggle="modal" data-target="#modalCUTFor<%=c.getId()%>">
									<%=c.getBaseName()%> <%=c.getBaseName().equals(c.getAlias()) ? "" : "(alias "+c.getAlias()+")"%>
								</a>
								<div id="modalCUTFor<%=c.getId()%>" class="modal fade" role="dialog" style="text-align: left;" tabindex="-1">
									<div class="modal-dialog">
										<!-- Modal content-->
										<div class="modal-content">
											<div class="modal-header">
												<button type="button" class="close" data-dismiss="modal">&times;</button>
												<h4 class="modal-title"><%=c.getBaseName()%></h4>
											</div>
											<div class="modal-body">
												<pre class="readonly-pre"><textarea
														class="readonly-textarea classPreview" id="sut<%=c.getId()%>"
														name="cut<%=c.getId()%>" cols="80"
														rows="30"><%=c.getAsHTMLEscapedString()%></textarea></pre>
											</div>
											<div class="modal-footer">
												<button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
											</div>
										</div>
									</div>
								</div>
							</td>
							<td><%=GameClassDAO.getMappedDependencyIdsForClassId(c.getId()).size()%>/<%=GameClassDAO.getMappedTestIdsForClassId(c.getId()).size()%>/<%=GameClassDAO.getMappedMutantIdsForClassId(c.getId()).size()%></td>
							<td><%=mutationDiff > 0 ? String.valueOf(mutationDiff) : ""%></td>
							<td><%=testingDiff > 0 ? String.valueOf(testingDiff) : ""%></td>
							<!--
							<td>
								<form id="aiPrepButton<%= c.getId() %>" action="<%=request.getContextPath() %>/ai_preparer" method="post" >
									<button type="submit" class="btn btn-primary btn-game btn-right" form="aiPrepButton<%= c.getId() %>" onClick="this.form.submit(); this.disabled=true; this.value='Preparing...';"
											<% //if (PrepareAI.isPrepared(c)) { %> disabled <% //} %>>
										Prepare AI
									</button>
									<input type="hidden" name="formType" value="runPrepareAi" />
									<input type="hidden" name="cutID" value="<%= c.getId() %>" />
								</form>
							</td>
							-->
						</tr>
					<% } %>
				</tbody>
				<% } %>
			</table>
		</div>
	</div>
	<script>
		$('.modal').on('shown.bs.modal', function() {
			var codeMirrorContainer = $(this).find(".CodeMirror")[0];
			if (codeMirrorContainer && codeMirrorContainer.CodeMirror) {
				codeMirrorContainer.CodeMirror.refresh();
			} else {
				var editorDiff = CodeMirror.fromTextArea($(this).find('textarea')[0], {
					lineNumbers: false,
					readOnly: true,
					mode: "text/x-java"
				});
				editorDiff.setSize("100%", 500);
			}
		});

        $(document).ready(function () {
            $('#tableUploadedClasses').DataTable({
                'paging': false,
                lengthChange: false,
                searching: false,
                order: [[0, "desc"]]
            });
        });
	</script>
</div>
<%@ include file="/jsp/footer.jsp" %>
