package de.metas.security.impl;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import java.util.ArrayList;
import java.util.List;

import org.adempiere.service.ClientId;
import org.adempiere.service.IClientDAO;
import org.compiere.model.I_AD_Client;
import org.compiere.model.I_AD_ClientInfo;
import org.compiere.util.Env;

import de.metas.security.IRoleDAO;
import de.metas.security.IUserRolePermissions;
import de.metas.security.IUserRolePermissionsBuilder;
import de.metas.security.IUserRolePermissionsDAO;
import de.metas.security.Role;
import de.metas.security.RoleId;
import de.metas.security.TableAccessLevel;
import de.metas.security.permissions.Constraints;
import de.metas.security.permissions.ElementPermissions;
import de.metas.security.permissions.GenericPermissions;
import de.metas.security.permissions.OrgPermissions;
import de.metas.security.permissions.PermissionsBuilder.CollisionPolicy;
import de.metas.security.permissions.TableColumnPermissions;
import de.metas.security.permissions.TablePermissions;
import de.metas.security.permissions.TableRecordPermissions;
import de.metas.security.permissions.UserMenuInfo;
import de.metas.user.UserId;
import de.metas.util.Check;
import de.metas.util.Services;
import lombok.NonNull;

class UserRolePermissionsBuilder implements IUserRolePermissionsBuilder
{
	private final transient IUserRolePermissionsDAO userRolePermissionsDAO = Services.get(IUserRolePermissionsDAO.class);
	//
	// Parameters
	private String name;
	private RoleId _adRoleId;
	private Role _role;
	private UserId _userId;
	private ClientId _adClientId;
	private I_AD_Client _adClient; // lazy
	private I_AD_ClientInfo _adClientInfo; // lazy
	private TableAccessLevel userLevel;
	private UserMenuInfo _menuInfo;  // lazy or configured

	//
	private OrgPermissions orgAccesses;
	private TablePermissions tableAccesses;
	private TableColumnPermissions columnAccesses;
	private TableRecordPermissions recordAccesses;
	private ElementPermissions windowAccesses;
	private ElementPermissions processAccesses;
	private ElementPermissions taskAccesses;
	private ElementPermissions workflowAccesses;
	private ElementPermissions formAccesses;

	private GenericPermissions miscPermissions;
	private Constraints constraints;

	private UserRolePermissionsIncludesList userRolePermissionsAlreadyIncluded;
	private final List<UserRolePermissionsInclude> userRolePermissionsToInclude = new ArrayList<>();
	private UserRolePermissionsIncludesList userRolePermissionsIncluded;

	private final boolean accountingModuleActive;

	UserRolePermissionsBuilder(final boolean accountingModuleActive)
	{
		this.accountingModuleActive = accountingModuleActive;
	}

	@Override
	public IUserRolePermissions build()
	{
		final RoleId adRoleId = getRoleId();
		final UserId adUserId = getUserId();
		final ClientId adClientId = getClientId();

		if (orgAccesses == null)
		{
			final Role role = getRole();
			orgAccesses = userRolePermissionsDAO.retrieveOrgPermissions(role, adUserId);
		}
		if (tableAccesses == null)
		{
			tableAccesses = userRolePermissionsDAO.retrieveTablePermissions(adRoleId);
		}
		if (columnAccesses == null)
		{
			columnAccesses = userRolePermissionsDAO.retrieveTableColumnPermissions(adRoleId);
		}
		if (recordAccesses == null)
		{
			recordAccesses = userRolePermissionsDAO.retrieveRecordPermissions(adRoleId);
		}
		if (windowAccesses == null)
		{
			windowAccesses = userRolePermissionsDAO.retrieveWindowPermissions(adRoleId, adClientId);
		}
		if (processAccesses == null)
		{
			processAccesses = userRolePermissionsDAO.retrieveProcessPermissions(adRoleId, adClientId);
		}
		if (taskAccesses == null)
		{
			taskAccesses = userRolePermissionsDAO.retrieveTaskPermissions(adRoleId, adClientId);
		}
		if (workflowAccesses == null)
		{
			workflowAccesses = userRolePermissionsDAO.retrieveWorkflowPermissions(adRoleId, adClientId);
		}
		if (formAccesses == null)
		{
			formAccesses = userRolePermissionsDAO.retrieveFormPermissions(adRoleId, adClientId);
		}

		if (miscPermissions == null)
		{
			miscPermissions = extractPermissions(getRole(), getAD_Client());
		}

		if (constraints == null)
		{
			constraints = getRole().getConstraints();
		}

		final UserRolePermissionsIncludesList.Builder userRolePermissionsIncludedBuilder = UserRolePermissionsIncludesList.builder();
		if (userRolePermissionsAlreadyIncluded != null)
		{
			userRolePermissionsIncludedBuilder.addAll(userRolePermissionsAlreadyIncluded);
		}

		//
		// Merge included permissions if any
		if (!userRolePermissionsToInclude.isEmpty())
		{
			final OrgPermissions.Builder orgAccessesBuilder = orgAccesses.asNewBuilder();
			final TablePermissions.Builder tableAccessesBuilder = tableAccesses.asNewBuilder();
			final TableColumnPermissions.Builder columnAccessesBuilder = columnAccesses.asNewBuilder();
			final TableRecordPermissions.Builder recordAccessesBuilder = recordAccesses.asNewBuilder();
			final ElementPermissions.Builder windowAccessesBuilder = windowAccesses.asNewBuilder();
			final ElementPermissions.Builder processAccessesBuilder = processAccesses.asNewBuilder();
			final ElementPermissions.Builder taskAccessesBuilder = taskAccesses.asNewBuilder();
			final ElementPermissions.Builder workflowAccessesBuilder = workflowAccesses.asNewBuilder();
			final ElementPermissions.Builder formAccessesBuilder = formAccesses.asNewBuilder();

			UserRolePermissionsInclude lastIncludedPermissionsRef = null;
			for (final UserRolePermissionsInclude includedPermissionsRef : userRolePermissionsToInclude)
			{
				final IUserRolePermissionsBuilder includedPermissions = includedPermissionsRef.getUserRolePermissions().asNewBuilder();

				CollisionPolicy collisionPolicy = CollisionPolicy.Merge;
				//
				// If roles have same SeqNo, then, the second role will override permissions from first role
				if (lastIncludedPermissionsRef != null && includedPermissionsRef.getSeqNo() >= 0
						&& includedPermissionsRef.getSeqNo() >= 0
						&& lastIncludedPermissionsRef.getSeqNo() == includedPermissionsRef.getSeqNo())
				{
					collisionPolicy = CollisionPolicy.Override;
				}

				orgAccessesBuilder.addPermissions(includedPermissions.getOrgPermissions(), collisionPolicy);
				tableAccessesBuilder.addPermissions(includedPermissions.getTablePermissions(), collisionPolicy);
				columnAccessesBuilder.addPermissions(includedPermissions.getColumnPermissions(), collisionPolicy);
				recordAccessesBuilder.addPermissions(includedPermissions.getRecordPermissions(), collisionPolicy);
				windowAccessesBuilder.addPermissions(includedPermissions.getWindowPermissions(), collisionPolicy);
				processAccessesBuilder.addPermissions(includedPermissions.getProcessPermissions(), collisionPolicy);
				taskAccessesBuilder.addPermissions(includedPermissions.getTaskPermissions(), collisionPolicy);
				workflowAccessesBuilder.addPermissions(includedPermissions.getWorkflowPermissions(), collisionPolicy);
				formAccessesBuilder.addPermissions(includedPermissions.getFormPermissions(), collisionPolicy);

				// add it to the list of included permissions.
				userRolePermissionsIncludedBuilder.add(includedPermissionsRef);

				lastIncludedPermissionsRef = includedPermissionsRef;
			}

			orgAccesses = orgAccessesBuilder.build();
			tableAccesses = tableAccessesBuilder.build();
			columnAccesses = columnAccessesBuilder.build();
			recordAccesses = recordAccessesBuilder.build();
			windowAccesses = windowAccessesBuilder.build();
			processAccesses = processAccessesBuilder.build();
			taskAccesses = taskAccessesBuilder.build();
			workflowAccesses = workflowAccessesBuilder.build();
			formAccesses = formAccessesBuilder.build();
		}

		userRolePermissionsIncluded = userRolePermissionsIncludedBuilder.build();

		return new UserRolePermissions(this);
	}

	private GenericPermissions extractPermissions(final Role role, final I_AD_Client adClient)
	{
		final GenericPermissions.Builder rolePermissions = role.getPermissions().toBuilder();

		if (adClient.isUseBetaFunctions())
		{
			rolePermissions.addPermission(IUserRolePermissions.PERMISSION_UseBetaFunctions, CollisionPolicy.Override);
		}

		if (!accountingModuleActive)
		{
			rolePermissions.removePermission(IUserRolePermissions.PERMISSION_ShowAcct);
		}

		return rolePermissions.build();
	}

	@Override
	public UserRolePermissionsBuilder setRoleId(@NonNull final RoleId adRoleId)
	{
		_adRoleId = adRoleId;
		_role = null;
		return this;
	}

	private final Role getRole()
	{
		if (_role == null)
		{
			final RoleId adRoleId = getRoleId();
			_role = Services.get(IRoleDAO.class).getById(adRoleId);
			Check.assumeNotNull(_role, "AD_Role shall exist for {}", adRoleId);
		}
		return _role;
	}

	@Override
	public final RoleId getRoleId()
	{
		Check.assumeNotNull(_adRoleId, "Role shall be set");
		return _adRoleId;
	}

	public final String getName()
	{
		if (name != null)
		{
			return name;
		}
		return getRole().getName();
	}

	public UserRolePermissionsBuilder setName(final String name)
	{
		this.name = name;
		return this;
	}

	@Override
	public UserRolePermissionsBuilder setUserId(final UserId adUserId)
	{
		_userId = adUserId;
		return this;
	}

	@Override
	public final UserId getUserId()
	{
		Check.assumeNotNull(_userId, "userId shall be set");
		return _userId;
	}

	@Override
	public UserRolePermissionsBuilder setClientId(final ClientId adClientId)
	{
		_adClientId = adClientId;
		return this;
	}

	@Override
	public final ClientId getClientId()
	{
		// Check if the AD_Client_ID was set and it was not set to something like "-1"
		if (_adClientId != null)
		{
			return _adClientId;
		}

		// Fallback: use role's AD_Client_ID
		return getRole().getClientId();
	}

	private I_AD_Client getAD_Client()
	{
		if (_adClient == null)
		{
			final ClientId adClientId = getClientId();
			_adClient = Services.get(IClientDAO.class).getById(adClientId);
		}
		return _adClient;
	}

	private I_AD_ClientInfo getAD_ClientInfo()
	{
		if (_adClientInfo == null)
		{
			final ClientId adClientId = getClientId();
			_adClientInfo = Services.get(IClientDAO.class).retrieveClientInfo(Env.getCtx(), adClientId.getRepoId());
		}
		return _adClientInfo;
	}

	@Override
	public UserRolePermissionsBuilder setUserLevel(final TableAccessLevel userLevel)
	{
		this.userLevel = userLevel;
		return this;
	}

	@Override
	public TableAccessLevel getUserLevel()
	{
		if (userLevel != null)
		{
			return userLevel;
		}
		return getRole().getUserLevel();
	}

	@Override
	public OrgPermissions getOrgPermissions()
	{
		return orgAccesses;
	}

	public UserRolePermissionsBuilder setOrgPermissions(final OrgPermissions orgAccesses)
	{
		this.orgAccesses = orgAccesses;
		return this;
	}

	@Override
	public TablePermissions getTablePermissions()
	{
		return tableAccesses;
	}

	public UserRolePermissionsBuilder setTablePermissions(final TablePermissions tableAccesses)
	{
		this.tableAccesses = tableAccesses;
		return this;
	}

	@Override
	public TableColumnPermissions getColumnPermissions()
	{
		return columnAccesses;
	}

	public UserRolePermissionsBuilder setColumnPermissions(final TableColumnPermissions columnAccesses)
	{
		this.columnAccesses = columnAccesses;
		return this;
	}

	@Override
	public TableRecordPermissions getRecordPermissions()
	{
		return recordAccesses;
	}

	public UserRolePermissionsBuilder setRecordPermissions(final TableRecordPermissions recordAccesses)
	{
		this.recordAccesses = recordAccesses;
		return this;
	}

	@Override
	public ElementPermissions getWindowPermissions()
	{
		return windowAccesses;
	}

	public UserRolePermissionsBuilder setWindowPermissions(final ElementPermissions windowAccesses)
	{
		this.windowAccesses = windowAccesses;
		return this;
	}

	@Override
	public ElementPermissions getProcessPermissions()
	{
		return processAccesses;
	}

	public UserRolePermissionsBuilder setProcessPermissions(final ElementPermissions processAccesses)
	{
		this.processAccesses = processAccesses;
		return this;
	}

	@Override
	public ElementPermissions getTaskPermissions()
	{
		return taskAccesses;
	}

	public UserRolePermissionsBuilder setTaskPermissions(final ElementPermissions taskAccesses)
	{
		this.taskAccesses = taskAccesses;
		return this;
	}

	@Override
	public ElementPermissions getWorkflowPermissions()
	{
		return workflowAccesses;
	}

	public UserRolePermissionsBuilder setWorkflowPermissions(final ElementPermissions workflowAccesses)
	{
		this.workflowAccesses = workflowAccesses;
		return this;
	}

	@Override
	public ElementPermissions getFormPermissions()
	{
		return formAccesses;
	}

	@Override
	public UserRolePermissionsBuilder setFormPermissions(final ElementPermissions formAccesses)
	{
		this.formAccesses = formAccesses;
		return this;
	}

	UserRolePermissionsBuilder setMiscPermissions(final GenericPermissions permissions)
	{
		Check.assumeNull(miscPermissions, "permissions not already configured");
		miscPermissions = permissions;
		return this;
	}

	public GenericPermissions getMiscPermissions()
	{
		Check.assumeNotNull(miscPermissions, "permissions configured");
		return miscPermissions;
	}

	UserRolePermissionsBuilder setConstraints(final Constraints constraints)
	{
		Check.assumeNull(this.constraints, "constraints not already configured");
		this.constraints = constraints;
		return this;
	}

	public Constraints getConstraints()
	{
		Check.assumeNotNull(constraints, "constraints configured");
		return constraints;
	}

	@Override
	public IUserRolePermissionsBuilder includeUserRolePermissions(final IUserRolePermissions userRolePermissions, final int seqNo)
	{
		userRolePermissionsToInclude.add(UserRolePermissionsInclude.of(userRolePermissions, seqNo));
		return this;
	}

	UserRolePermissionsBuilder setAlreadyIncludedRolePermissions(final UserRolePermissionsIncludesList userRolePermissionsAlreadyIncluded)
	{
		Check.assumeNotNull(userRolePermissionsAlreadyIncluded, "included not null");
		Check.assumeNull(this.userRolePermissionsAlreadyIncluded, "already included permissions were not configured before");

		this.userRolePermissionsAlreadyIncluded = userRolePermissionsAlreadyIncluded;
		return this;
	}

	UserRolePermissionsIncludesList getUserRolePermissionsIncluded()
	{
		Check.assumeNotNull(userRolePermissionsIncluded, "userRolePermissionsIncluded not null");
		return userRolePermissionsIncluded;
	}

	public UserRolePermissionsBuilder setMenuInfo(final UserMenuInfo menuInfo)
	{
		this._menuInfo = menuInfo;
		return this;
	}

	public UserMenuInfo getMenuInfo()
	{
		if (_menuInfo == null)
		{
			_menuInfo = findMenuInfo();
		}
		return _menuInfo;
	}

	private UserMenuInfo findMenuInfo()
	{
		final Role adRole = getRole();
		final int roleMenuTreeId = adRole.getAD_Tree_Menu_ID();
		if (roleMenuTreeId > 0)
		{
			return UserMenuInfo.of(roleMenuTreeId, adRole.getRoot_Menu_ID());
		}

		final I_AD_ClientInfo adClientInfo = getAD_ClientInfo();
		final int adClientMenuTreeId = adClientInfo.getAD_Tree_Menu_ID();
		if (adClientMenuTreeId > 0)
		{
			return UserMenuInfo.of(adClientMenuTreeId, adRole.getRoot_Menu_ID());
		}

		// Fallback: when role has NO menu and there is no menu defined on AD_ClientInfo level - shall not happen
		return UserMenuInfo.of(10, -1); // Menu // FIXME: hardcoded
	}
}
