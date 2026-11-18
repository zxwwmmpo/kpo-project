import {
  ApiError,
  api,
  request,
  type Achievement,
  type Contact,
  type Profile,
  type Project,
  type ProjectLink,
} from './account';

export type ProfileExportData = {
  profile: Profile;
  photo: Blob | null;
  contacts: Contact[];
  projects: (Project & { links: ProjectLink[] })[];
  achievements: Achievement[];
};

async function loadPhoto(signal: AbortSignal): Promise<Blob | null> {
  try {
    return await (await request('/profile/photo', { signal })).blob();
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

export async function loadProfileExport(
  signal: AbortSignal,
): Promise<ProfileExportData> {
  const [profile, photo, contacts, projects, achievements] = await Promise.all([
    api<Profile>('/profile', 'GET', undefined, signal),
    loadPhoto(signal),
    api<Contact[]>('/profile/contacts', 'GET', undefined, signal),
    api<Project[]>('/profile/projects', 'GET', undefined, signal),
    api<Achievement[]>('/profile/achievements', 'GET', undefined, signal),
  ]);

  const projectsWithLinks = await Promise.all(
    projects.map(async (project) => {
      if (project.linkCount === 0) return { ...project, links: [] };
      const { links } = await api<{ links: ProjectLink[] }>(
        `/profile/projects/${project.id}`,
        'GET',
        undefined,
        signal,
      );
      return { ...project, links };
    }),
  );

  return {
    profile,
    photo,
    contacts,
    projects: projectsWithLinks,
    achievements,
  };
}
