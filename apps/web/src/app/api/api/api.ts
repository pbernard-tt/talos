export * from './auth.service';
import { AuthService } from './auth.service';
export * from './auth.serviceInterface';
export * from './projects.service';
import { ProjectsService } from './projects.service';
export * from './projects.serviceInterface';
export const APIS = [AuthService, ProjectsService];
